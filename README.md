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
