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

Optional configuration fields tune ordering behaviour:

| Field                         | Default | Effect                                                                                                                                                          |
|--------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `orderingUnavailableEans`     |         | Comma-separated EANs always quoted as out of stock                                                                                                            |
| `orderingPriceDriftPercent`   | 0       | Live order price drifts from the feed price by this percent                                                                                                   |
| `orderingPickupPointsEnabled` | 1       | Acme only: 0/false makes supportsPickupPointDropship() false — a dropship request with a pickup point is then rejected (SupplierOrderRejectedException) without placing anything |

### Tracking simulation

Both suppliers answer `supportsOrderTracking()`. `trackOrder` finds the order by its Acme number
(`ACME-PO-…`/`ACME-DS-…`) or by client reference and replays a scripted lifecycle:

| Knob | Default | Effect |
|---|---|---|
| `trackingShipAfterChecks` | `2` | checks 1..N-1 answer `PROCESSING`; from the N-th the scenario applies |
| `trackingScenario` | `single` | `single` — `SHIPPED`, one DPD parcel `ACME-TRK-<ref>` without lines; `parts` — N-th check `PARTIALLY_SHIPPED` with parcel `-1` (first line), next check `SHIPPED` with parcels `-1` and `-2` (remaining lines); `cancel` — `CANCELLED`; `nodata` — `SHIPPED` without parcels |

Check counters and generated parcels are static (per JVM), like the placed-order store.

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
