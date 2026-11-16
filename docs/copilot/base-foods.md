# Copilot base food table: source and method

**Asset**: `app/src/main/assets/copilot/base_foods.json` (242 foods, ~74 KB), copied into the `base_foods` table at app start by `BaseFoodRepository.ensureSeeded()` (replaced whenever the asset changes).

## Source and licence (decision 13)
- Dataset: USDA FoodData Central, **SR Legacy, April 2018** CSV (`FoodData_Central_sr_legacy_food_csv_2018-04.zip` from https://fdc.nal.usda.gov/download-datasets/).
- Licence, checked on 2026-11-16 on the official FDC API guide (https://fdc.nal.usda.gov/api-guide/): "USDA FoodData Central data are in the public domain and they are not copyrighted. They are published under CC0 1.0 Universal (CC0 1.0)". "No permission is needed for their use, but we request that users list FoodData Central as the source of the data". The download page itself has no licence text.
- Attribution: shown in Copilot settings ("Food values for unpackaged foods: USDA FoodData Central (SR Legacy), public domain") and in the asset's `source` field; every row keeps `sourceRef` = SR fdc_id + English description.

## Method
1. Hand curation in `tools/copilot/base_foods_curation.csv`: `fdc_id; Italian name; synonyms (|); raw | cooked | empty`. Each fdc_id was picked by searching the SR descriptions (exact entry, e.g. `Rice, white, long-grain, regular, raw, unenriched`).
2. `python3 -I tools/copilot/build_base_foods.py <unzipped SR Legacy dir>` extracts per 100 g: energy kcal (nutrient 1008), protein (1003), fat (1004), carbohydrate by difference (1005). It fails on a missing id or value, warns when kcal differs from 4P+4C+9F by more than 15 % (only vegetables, tomato products, lemon, balsamic vinegar and cocoa: USDA uses food-specific Atwater factors and fibre; values kept as published), and writes the JSON sorted by id (`sr-<fdc_id>`).
3. Raw and cooked are separate rows where it matters (rice, pasta, grains, legumes, meat, fish, some vegetables); state-independent foods have no state.

## Curation choices worth knowing
- **No basmati in SR Legacy**: "riso basmati" (and thai/jasmine) is a synonym of long-grain white rice (365 kcal, P 7.1, C 80, F 0.7 per 100 g raw).
- Parmigiano / grana uses `Cheese, parmesan, hard`, not the US "grated parmesan" (which contains fillers, C 13.9 g).
- "Pane comune" = `Bread, Italian`; "Baguette" = `Bread, french or vienna`.
- Borlotti = pinto beans; cannellini = white beans; gorgonzola = `Cheese, blue`; branzino = sea bass (mixed species).
- Not in SR Legacy and therefore **omitted** (fewer, reliable rows): prosciutto crudo, bresaola, speck, gnocchi, polenta (cooked), Italian dry biscuits, espresso coffee.
- Generic names resolve only when unambiguous: e.g. "fagioli", "tonno", "yogurt greco" match several rows and the copilot asks the user.
- Possible later improvement (research §1): compare the 20-30 foods Gabriele eats most with CREA values (consulting, not copying).

## Household measures
Not part of this asset. Defaults live only in `copilot/food/HouseholdMeasureSeed.kt` (values from `phase0-research.md` §2, **provisional, not yet validated by the owner**, decision 20) and are editable in Copilot settings > Household measures.
