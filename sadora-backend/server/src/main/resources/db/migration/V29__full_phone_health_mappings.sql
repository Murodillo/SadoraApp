-- Everything the phone's store holds, not only what the app drew first (2026-09-30).
--
-- The phone reads every type Health Connect and HealthKit offer and posts it; these rows
-- are what lets the server keep it instead of answering `unmapped`. The apps convert to
-- SADORA's units on the device where the store hands over a typed value (Health Connect's
-- Energy, Volume, Length…), so most scales are 1; HealthKit's blood glucose arrives in
-- mg/dL and is scaled to mmol/L here.
--
-- Categories become numbers: an ovulation test is 0 inconclusive, 1 negative, 2 high,
-- 3 positive; cervical mucus is 1 dry, 2 sticky, 3 creamy, 4 watery, 5 egg white,
-- 6 unusual; spotting and sexual activity count one per note.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    -- Android Health Connect
    ('health_connect', 'TotalCaloriesBurned',      'total_energy',             'kcal',       1),
    ('health_connect', 'BasalMetabolicRate',       'basal_metabolic_rate',     'kcal',       1),
    ('health_connect', 'FloorsClimbed',            'floors',                   'count',      1),
    ('health_connect', 'ElevationGained',          'elevation_gained',         'm',          1),
    ('health_connect', 'ExerciseSession',          'exercise_minutes',         'min',        1),
    ('health_connect', 'MindfulnessSession',       'mindfulness_minutes',      'min',        1),
    ('health_connect', 'WheelchairPushes',         'wheelchair_pushes',        'count',      1),
    ('health_connect', 'Speed',                    'speed',                    'm/s',        1),
    ('health_connect', 'Power',                    'power',                    'w',          1),
    ('health_connect', 'StepsCadence',             'steps_cadence',            'spm',        1),
    ('health_connect', 'CyclingPedalingCadence',   'cycling_cadence',          'rpm',        1),
    ('health_connect', 'Vo2Max',                   'vo2_max',                  'ml/kg/min',  1),
    ('health_connect', 'BloodGlucose',             'blood_glucose',            'mmol/l',     1),
    ('health_connect', 'BloodPressureSystolic',    'blood_pressure_systolic',  'mmhg',       1),
    ('health_connect', 'BloodPressureDiastolic',   'blood_pressure_diastolic', 'mmhg',       1),
    ('health_connect', 'Height',                   'height',                   'cm',         1),
    ('health_connect', 'BodyFat',                  'body_fat',                 'percent',    1),
    ('health_connect', 'LeanBodyMass',             'lean_body_mass',           'kg',         1),
    ('health_connect', 'BodyWaterMass',            'body_water_mass',          'kg',         1),
    ('health_connect', 'BoneMass',                 'bone_mass',                'kg',         1),
    ('health_connect', 'Hydration',                'hydration',                'ml',         1),
    ('health_connect', 'NutritionEnergy',          'dietary_energy',           'kcal',       1),
    ('health_connect', 'NutritionProtein',         'protein',                  'g',          1),
    ('health_connect', 'NutritionCarbohydrates',   'carbohydrates',            'g',          1),
    ('health_connect', 'NutritionFat',             'fat',                      'g',          1),
    ('health_connect', 'OvulationTest',            'ovulation_test',           'result',     1),
    ('health_connect', 'CervicalMucus',            'cervical_mucus',           'appearance', 1),
    ('health_connect', 'IntermenstrualBleeding',   'intermenstrual_bleeding',  'count',      1),
    ('health_connect', 'SexualActivity',           'sexual_activity',          'count',      1),

    -- Apple HealthKit
    ('apple_health', 'HKQuantityTypeIdentifierBasalEnergyBurned',       'basal_metabolic_rate',     'kcal',       1),
    ('apple_health', 'HKQuantityTypeIdentifierFlightsClimbed',          'floors',                   'count',      1),
    ('apple_health', 'HKQuantityTypeIdentifierAppleExerciseTime',       'exercise_minutes',         'min',        1),
    ('apple_health', 'HKCategoryTypeIdentifierMindfulSession',          'mindfulness_minutes',      'min',        1),
    ('apple_health', 'HKQuantityTypeIdentifierPushCount',               'wheelchair_pushes',        'count',      1),
    ('apple_health', 'HKQuantityTypeIdentifierWalkingSpeed',            'speed',                    'm/s',        1),
    ('apple_health', 'HKQuantityTypeIdentifierVO2Max',                  'vo2_max',                  'ml/kg/min',  1),
    ('apple_health', 'HKQuantityTypeIdentifierBloodGlucose',            'blood_glucose',            'mg/dL',      0.0555084),
    ('apple_health', 'HKQuantityTypeIdentifierBloodPressureSystolic',   'blood_pressure_systolic',  'mmhg',       1),
    ('apple_health', 'HKQuantityTypeIdentifierBloodPressureDiastolic',  'blood_pressure_diastolic', 'mmhg',       1),
    ('apple_health', 'HKQuantityTypeIdentifierHeight',                  'height',                   'cm',         1),
    ('apple_health', 'HKQuantityTypeIdentifierBodyFatPercentage',       'body_fat',                 'percent',    1),
    ('apple_health', 'HKQuantityTypeIdentifierLeanBodyMass',            'lean_body_mass',           'kg',         1),
    ('apple_health', 'HKQuantityTypeIdentifierDietaryWater',            'hydration',                'ml',         1),
    ('apple_health', 'HKQuantityTypeIdentifierDietaryEnergyConsumed',   'dietary_energy',           'kcal',       1),
    ('apple_health', 'HKQuantityTypeIdentifierDietaryProtein',          'protein',                  'g',          1),
    ('apple_health', 'HKQuantityTypeIdentifierDietaryCarbohydrates',    'carbohydrates',            'g',          1),
    ('apple_health', 'HKQuantityTypeIdentifierDietaryFatTotal',         'fat',                      'g',          1),
    ('apple_health', 'HKCategoryTypeIdentifierOvulationTestResult',     'ovulation_test',           'result',     1),
    ('apple_health', 'HKCategoryTypeIdentifierCervicalMucusQuality',    'cervical_mucus',           'appearance', 1),
    ('apple_health', 'HKCategoryTypeIdentifierIntermenstrualBleeding',  'intermenstrual_bleeding',  'count',      1),
    ('apple_health', 'HKCategoryTypeIdentifierSexualActivity',          'sexual_activity',          'count',      1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
