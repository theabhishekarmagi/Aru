# Nutrition goals update

Aru’s Android journal and website now include a compact Goals card above the daily totals pill. The card shows calorie progress and carb/protein/fat rings; tapping opens Nutrition Goals. Rings clamp at 100% but amounts retain over-target values. A + marks incomplete totals. Unset targets remain blank, rather than being replaced with a prescribed calorie allowance.

The editor saves manual daily calorie/macro goals, optional sugar/fiber/sodium targets and visibility switches, a maintain/lose/gain weight plan, target weight in kg or lb, an optional target date, and dietary preferences. Cancel discards draft edits. Weight/date preferences do not automatically change intake targets. Dietary preferences are saved for the user; they do not replace the description of food actually logged or enforce ingredient exclusions.

Daily burn is an optional Mifflin–St Jeor resting-energy estimate multiplied by the selected activity factor. It needs profile birth date, height, weight, and an explicit equation-sex selection. It is shown only for ages 19–78 (the original study’s range); it is not a measured expenditure or an intake prescription. Factors are coarse app choices: 1.2 mostly seated, 1.375 light, 1.55 moderate, 1.725 very active. No deficit, weight-loss rate, or target-date recommendation is generated.

Method: [Mifflin et al., 1990](https://pubmed.ncbi.nlm.nih.gov/2305711/), DOI 10.1093/ajcn/51.2.241. Resting kcal/day = 10 × kg + 6.25 × cm − 5 × age + 5 (male equation) or −161 (female equation).

Sugar is stored in grams and sodium in milligrams. The nutrition function requests these values for new text/photo estimates. Missing values in old estimates remain null and totals are marked incomplete; existing meals are not silently recalculated. Manual nutrition supports these fields too. Verified menu records without these nutrients preserve nulls.

Android preferences remain in the existing private, account-separated journal store. Web settings remain in the owner-protected `nutrition_goals` table, using its revision check for concurrent saves. The additive migration retains existing targets and enables fiber tracking for users who already set a fiber target. This release does not add Android/web journal or goal syncing.

The server additions are compatible with prior clients. Android’s new UI requires the v0.9.0-goals APK once; it cannot be supplied through the current SDUI card schema.

Validation: 47 Android unit tests, five emulator tests (goals, water regression, and manual nutrition/goals persistence), eight web domain tests, 18 nutrition-function tests, Android lint/build, and the web production build pass. An actual PostgreSQL transaction verified new goal fields can be saved by their owner and remain invisible to a different user, then rolled back all synthetic records. Browser checks covered saving, reopening, cancel, unit switching, future dates, calculation details, and nutrient switches using an isolated local fixture. Edge Function version 23 and the additive database migration are deployed; web UI release is handled separately through Vercel.
