# Nutrition references

## INDB workbook inspected

- Official landing page: https://www.anuvaad.org.in/indian-nutrient-databank/
- Download: https://www.anuvaad.org.in/wp-content/uploads/2020/07/Anuvaad_INDB_2024.11.xlsx
- Local-only reference (gitignored): `references/Anuvaad_INDB_2024.11.xlsx`
- Retrieved 8 October 2026. Version `2024.11` comes from the official filename, not an independently verified publication date.
- SHA-256: `c39023890f45b86b7f68578a4f740c5772234402111054c28a7a867d8dda25d9`
- One sheet, `Sheet1`, 1,015 rows including header, 82 columns. 1,014 food records with 1,014 distinct `food_code` identifiers.
- Identity columns: `food_code`, `food_name`, `primarysource`.
- Required nutrient columns: `energy_kcal`, `protein_g`, `carb_g`, `fat_g`, `fibre_g`.
- Serving label: `servings_unit`; serving nutrient fields: `unit_serving_energy_kcal`, `unit_serving_protein_g`, `unit_serving_carb_g`, `unit_serving_fat_g`, `unit_serving_fibre_g`.
- The official landing page describes nutrition per 100g and per serving. Workbook headings themselves do not encode `per_100g`; import must retain this provenance and verify the interpretation before production use.
- No blank values in the five base v1 nutrient columns. Each of the five serving nutrient columns has 82 blank values. `servings_unit` is an empty string for 97 records. Empty strings and nulls must remain distinct from real zeros.
- Observed source labels: `asc_manual` (490), `bfp_manual` (376), `open_source_recipes` (148). Do not expand their meaning without documentation.
- Examples of serving labels include bowl, plate, piece, cup, tea cup, idli, dosa, tablespoon, ml, and gm. Labels are recipe-specific. There is no explicit universal serving-weight column in this workbook.

## Import rules still to implement

Preserve exact record ID/name/source/version and raw values. Normalize aliases separately. Prefer measured grams against the documented 100g basis. Use source serving values only for an explicitly matched serving; do not extrapolate a universal bowl/katori/cup weight. Household volume-to-weight conversion needs dish-specific density or an explicit, editable assumption. Missing serving data requires another supported basis or clarification, never zero-filled nutrition. Retain recipe variation as uncertainty.

The workbook is a development reference outside app assets and is excluded from the public source repository; its download URL and checksum are retained above. Redistribution terms have not yet been established. It is not bundled nutrition or a claimed live lookup. The user may supply a preferred version. Confirm redistribution/attribution terms before distributing any dataset publicly.

## USDA FoodData Central

Official API guide: https://fdc.nal.usda.gov/api-guide/

Food search and detail endpoints require a data.gov API key. Keep that key server-side. The guide describes public-domain/CC0 data and requests source attribution. Preserve FDC identifier, data type, nutrient units, serving/weight basis, and retrieval/version metadata. Raw/cooked distinctions must survive matching.

## Restaurants

Prefer official published nutrition for the correct country/market, menu item, variant, and size. A US item must not silently stand in for an Indian item. Unavailable official data may yield a clearly identified estimate with references and assumptions; never present it as an official restaurant value.
