# supplier-acme

Dummy supplier plugin for local development. Provides two fake suppliers (**Acme
** and **AcmeB**) that serve static CSV feeds from classpath resources, so the
main app can run without real supplier credentials.

Loaded via `ServiceLoader` (`META-INF/services`).

## Suppliers

| Supplier  | Type        | Accuracy | Shipping                                         | Feed                 |
|-----------|-------------|----------|--------------------------------------------------|----------------------|
| **Acme**  | Distributor | 5        | Free, 1-day                                      | `acme-products.csv`  |
| **AcmeB** | Retailer    | 3        | Flat rate (14.99 PLN, free above 500 PLN), 3-day | `acmeb-products.csv` |

## Ordering

Both suppliers support ordering and answer availability from their own feed.
Each one requires a delivery address picked from the same four mock addresses
(ids `1`-`4`), and returns purchase orders under its own prefix — `ACME-PO-` and
`ACMEB-PO-`. Orders are idempotent per supplier and client order reference, so
the same reference used at both suppliers places two independent orders.

Three optional configuration fields simulate a misbehaving supplier:

| Field                        | Effect                                                                                                                                                       |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `orderingUnavailableEans`    | Comma-separated EANs always quoted as out of stock                                                                                                              |
| `orderingPriceDriftPercent`  | Live order price drifts from the feed price by this percent                                                                                                     |
| `orderingScenarioOverride`   | Labeled `Symulacja: wymuś scenariusz zakupu`. Forces the outcome for **every** purchase placed against this supplier connection, overriding the SIM-* product lookup below. One of `OK`, `UNKNOWN_PLACED`, `UNKNOWN_LOST`, `REJECTED`, `BLANK_ID` (case-insensitive), or blank to fall back to per-product SIM-* behaviour. Help text: "puste = wg produktu SIM-\*; OK \| UNKNOWN_PLACED \| UNKNOWN_LOST \| REJECTED \| BLANK_ID — dotyczy każdego zakupu w sklepie". |

### SIM-* scenario products

Both feeds also carry five dedicated products (EANs `5900000000901`-`5900000000905`,
brand `Acme`, category `Akcesoria`) whose MFN alone drives `placeOrder`'s outcome —
no configuration needed. This lets a store keep a normal, well-behaved connection and
still trigger the ordering edge cases by ordering a specific throwaway SKU. When
`orderingScenarioOverride` is set, it takes precedence over the SIM-* MFN for every
order placed against that connection, not just orders containing a SIM-* line.

| MFN                   | `placeOrder` behaviour                                                                                                                 | What the app shows                                                                                                                          |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `SIM-OK`               | Places the order and returns normally.                                                                                                   | Delivery completes.                                                                                                                            |
| `SIM-UNKNOWN-PLACED`   | Places the order (it exists at the supplier) but the **first** attempt throws as if the response timed out.                              | „Wysłane — niepotwierdzone"; „Sprawdź u dostawcy" (`findPlacedOrder`) finds it and succeeds.                                                    |
| `SIM-UNKNOWN-LOST`     | The **first** attempt throws before the order is registered anywhere; a retry with the same client order reference places it for real.   | Unconfirmed; „Sprawdź u dostawcy" reports not found; retrying the purchase succeeds.                                                            |
| `SIM-REJECTED`         | Always throws a rejection, on every attempt.                                                                                              | Delivery/order line ends up FAILED.                                                                                                             |
| `SIM-BLANK-ID`         | The **first** attempt returns a result with an empty external order id (nothing persisted); a retry places the order for real.           | Unconfirmed (no external order id to check); retrying the purchase succeeds.                                                                    |

"First attempt" is tracked per supplier + client order reference (not per process run):
once an attempt has been recorded, later retries with the same reference take the
non-simulated path (`UNKNOWN_LOST` and `BLANK_ID` succeed outright; `UNKNOWN_PLACED`
returns the already-placed order instead of throwing again). A brand-new client order
reference always gets a fresh "first attempt".

### Persisting placed orders across restarts

The system property `acme.state.file` controls where placed-order and attempted-ref
state is persisted, so a JVM restart replays the same idempotent outcomes instead of
re-running the simulated scenarios from scratch:

- Unset: defaults to `${java.io.tmpdir}/supplier-acme-state.json`.
- Set to `none`: disables persistence entirely (state is in-memory only, as before).
- Set to a path: state is written there (atomically, via a temp file + move) after
  every attempt and placed order, and loaded from there on next startup. A missing or
  corrupt file is treated as empty rather than failing startup.

## CSV format

```
EAN;MFN;Brand;Name;Category;Price;Currency;Qty
```

## Test cases in feed data

Both feeds share 12 overlapping EANs (`5900000000001`-`5900000000012`) with
intentionally different prices and stock levels, plus each has 3 exclusive
products.

| Scenario                               | Examples                                                                                                |
|----------------------------------------|---------------------------------------------------------------------------------------------------------|
| **Price differences** across suppliers | CPU: 1299 (Acme) vs 1599 (AcmeB); GPU: 3899 vs 3199                                                     |
| **Out of stock** (qty=0)               | Acme: MirageDrive, DesertDry; AcmeB: PhantomStock, DesertDry                                            |
| **Low stock** (qty=1)                  | Acme: FinalUnit                                                                                         |
| **High stock**                         | Acme: StockPile (200); PennyWise (80)                                                                   |
| **Different currency**                 | ForeignExchange: PLN (Acme) vs EUR (AcmeB)                                                              |
| **Supplier-exclusive products**        | Acme-only: SoloRun, LoneWolf, Singular (`5901*`); AcmeB-only: OnlyHere, Exclusive, UniqueBook (`5902*`) |
| **Shared out-of-stock**                | DesertDry is 0 at both suppliers                                                                        |

These cases exercise supplier selection, price comparison, currency handling,
stock availability, and auto-discovery matching logic.
