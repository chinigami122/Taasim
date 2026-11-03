import unittest
from datetime import datetime, timezone
from types import SimpleNamespace

from app_demand_forecast import (
    DAY, WEEK, ZONES, feature_grid, feature_row, hour_number, observed_counts, seasonal_baseline,
)


class ForecastFeaturesTest(unittest.TestCase):
    def test_future_and_ongoing_hour_do_not_affect_features(self):
        target = 500
        counts = {hour: {1: 2} for hour in range(target - WEEK, target - 1)}
        expected = feature_row(counts, target, 1)
        self.assertEqual(expected[3:], (2.0, 2.0, 2.0))
        for hour in (target - 1, target, target + 1):
            counts[hour] = {1: 999}
        self.assertEqual(feature_row(counts, target, 1), expected)
        self.assertEqual(next(feature_grid(counts, target, target + 1, labeled=True))[-1], 999)

    def test_zero_is_observed_but_missing_lag_is_unavailable(self):
        target = 500
        counts = {hour: dict.fromkeys(ZONES, 0) for hour in range(target - WEEK, target - 1)}
        self.assertEqual(feature_row(counts, target, 1)[3:], (0.0, 0.0, 0.0))
        self.assertEqual(seasonal_baseline(counts, target), dict.fromkeys(ZONES, 0))
        del counts[target - DAY]
        self.assertIsNone(feature_row(counts, target, 1))
        del counts[target - WEEK]
        self.assertIsNone(seasonal_baseline(counts, target))

    def test_partial_hours_and_zeros_before_first_app_activity_are_excluded(self):
        class Row(SimpleNamespace):
            def __getitem__(self, key):
                return getattr(self, key)

        rows = [
            Row(hour=datetime(2026, 1, 1, hour, tzinfo=timezone.utc), zone=zone,
                count=int(hour == 1 and zone == 1), processed_at=zone if hour == 2 else 0)
            for hour in range(4)
            for zone in ZONES
            if (hour, zone) != (3, 16)
        ]
        observed = observed_counts(rows)
        self.assertEqual(list(observed), [hour_number(datetime(2026, 1, 1, 1, tzinfo=timezone.utc))])
        self.assertEqual(next(iter(observed.values()))[16], 0)


if __name__ == "__main__":
    unittest.main()
