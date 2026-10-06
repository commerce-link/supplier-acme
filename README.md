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

`Acme` declares one order option, `shippingService` (`standard` default / `express`), required;
orders without it or with another value are rejected before placement. `AcmeB` declares no options
(exercises the no-options path).

Optional configuration fields tune ordering behaviour:

| Field                         | Default | Effect                                                                                                                                                                                                                    |
|-------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `orderingUnavailableEans`     |         | Comma-separated EANs always quoted as out of stock                                                                                                                                                                        |
| `orderingPriceDriftPercent`   | 0       | Live order price drifts from the feed price by this percent                                                                                                                                                               |
| `orderingPickupPointsEnabled` | 1       | Applies to both Acme and AcmeB: any value other than `1`/`true` disables pickup-point dropship support, so a dropship order naming a pickup point is rejected (`SupplierOrderRejectedException`) instead of being placed. |
| `orderingScenarioOverride`    |         | Labeled `Symulacja: wymuś scenariusz zakupu`. Forces the outcome for **every** purchase placed against this supplier connection, overriding the SIM-* product lookup below. One of `OK`, `UNKNOWN_PLACED`, `UNKNOWN_LOST`, `REJECTED`, `BLANK_ID` (case-insensitive), or blank to fall back to per-product SIM-* behaviour. Help text: "puste = wg produktu SIM-\*; OK \| UNKNOWN_PLACED \| UNKNOWN_LOST \| REJECTED \| BLANK_ID — dotyczy każdego zakupu w sklepie". |

### Tracking simulation

Both suppliers answer `supportsOrderTracking()`. `trackOrder` finds the order by its Acme number
(`ACME-PO-…`/`ACME-DS-…`) or by client reference and replays a scripted lifecycle:

| Knob | Default | Effect |
|---|---|---|
| `trackingShipAfterChecks` | `2` | checks 1..N-1 answer `PROCESSING`; from the N-th the scenario applies |
| `trackingScenario` | `single` | `single` — `SHIPPED`, one DPD parcel `ACMETRK<ref>` (`ACMEBTRK<ref>` for AcmeB) without lines; `parts` — N-th check `PARTIALLY_SHIPPED` with parcel `ACMETRK<ref>P1` (first line), next check `SHIPPED` with parcels `ACMETRK<ref>P1` and `ACMETRK<ref>P2` (remaining lines); `cancel` — `CANCELLED`; `nodata` — `SHIPPED` without parcels |

`<ref>` is the first 12 hex digits of the purchase reference, upper-cased. Real carriers (e.g.
Furgonetka) only accept upper-case alphanumeric tracking numbers of 7-34 characters and echo them
back upper-cased in webhooks, so the simulation mirrors that shape instead of using the full UUID.

Check counters and generated parcels are static (per JVM), like the placed-order store.

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

The feeds carry real PC components (CPUs, graphics cards, motherboards, memory, SSDs, power
supplies, cases, CPU coolers and fans) with real EANs, manufacturer codes and names. Net prices
and stock levels are synthetic. The files are byte-identical to the app's local seed
(`app/src/main/resources/local-init/s3/feeds/`), which is where they are maintained.

Acme sells 97 products and AcmeB 84; 51 of them are sold by both, plus the five `SIM-*` rows.

| Scenario                               | Examples                                                                                  |
|----------------------------------------|-------------------------------------------------------------------------------------------|
| **Price differences** across suppliers | AcmeB is about 4% cheaper on every shared product, e.g. ASUS Dual RTX 5070: 2899.29 vs 2783.32 |
| **Supplier-exclusive products**        | Acme-only: AMD Ryzen 7 9850X3D, ASUS ROG Astral RTX 5090; AcmeB-only: Samsung 9100 Pro 2TB |
| **Low stock**                          | Lian Li O11 Dynamic EVO RGB (1, AcmeB), ASUS ROG Astral RTX 5090 (2, Acme)                |
| **High stock**                         | Arctic P12 Pro (120), Lian Li UNI FAN CL Wireless 120 (120)                               |
| **Different currency**                 | Noctua NF-A14x25 G2 PWM: PLN at Acme, EUR at AcmeB                                        |
| **Out of stock**                       | no feed row has qty 0; use `orderingUnavailableEans` to quote a product as unavailable    |

These cases exercise supplier selection, price comparison, currency handling,
stock availability, and auto-discovery matching logic.
