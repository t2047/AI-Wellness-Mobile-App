"""
Wellness Agent — Core Analysis Engine
======================================
Implements the agentic AI workflow:
1. Fetch user wellness records from the backend API
2. Analyze trends (sleep, activity, consistency)
3. Generate personalized recommendations
4. Return structured results

Author: WellnessApp Team
Date: 2026-06-25
@author Jia Qianrui
"""

import logging
from typing import Any, Dict, List, Optional

import requests

logger = logging.getLogger(__name__)


class WellnessAgent:
    """
    Autonomous wellness analysis agent.

    Workflow:
    1. GET /api/wellness-records — fetch user health data
    2. Analyze sleep and activity trends
    3. Generate evidence-based recommendations
    4. POST /api/recommendations — save results to backend
    """

    def __init__(self, backend_url: str = "http://localhost:8080",
                 jwt_token: str = ""):
        self.backend_url = backend_url.rstrip("/")
        self.jwt_token = jwt_token

    def analyze_and_recommend(
        self,
        user_id: int,
        username: str,
        jwt_token: str,
        backend_url: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Full agentic workflow: fetch → analyze → recommend → (optionally) save.

        Args:
            user_id: The ID of the user to analyze.
            username: Username for logging/display.
            jwt_token: JWT for backend API authentication.
            backend_url: Override the default backend URL.

        Returns:
            Dict with analysis results and recommendations.
        """
        if backend_url:
            self.backend_url = backend_url.rstrip("/")

        headers = {
            "Authorization": f"Bearer {jwt_token}",
            "Content-Type": "application/json"
        }

        # Step 1: Fetch wellness records
        logger.info(f"Fetching records for user {user_id} ({username})")
        records = self._fetch_records(user_id, headers)

        if not records:
            logger.warning(f"No records found for user {user_id}")
            return {
                "success": True,
                "analysisSummary": "Not enough data to generate insights. "
                                   "Please log at least a few days of health data.",
                "sleepAnalysis": None,
                "activityAnalysis": None,
                "recommendations": [
                    "Start logging your daily sleep and activity to get personalized insights!"
                ]
            }

        # Step 2: Analyze trends
        logger.info(f"Analyzing {len(records)} records for user {user_id}")
        sleep_analysis = self._analyze_sleep(records)
        activity_analysis = self._analyze_activity(records)

        # Step 3: Generate recommendations
        recommendations = self._generate_recommendations(
            records, sleep_analysis, activity_analysis
        )

        # Step 4: Build analysis summary
        analysis_summary = self._build_summary(
            sleep_analysis, activity_analysis, len(records)
        )

        result = {
            "success": True,
            "analysisSummary": analysis_summary,
            "sleepAnalysis": sleep_analysis,
            "activityAnalysis": activity_analysis,
            "recommendations": recommendations
        }

        logger.info(f"Analysis complete for user {user_id}: {analysis_summary[:100]}...")
        return result

    def _fetch_records(self, user_id: int, headers: Dict) -> List[Dict]:
        """Fetch wellness records from the backend API."""
        try:
            url = f"{self.backend_url}/api/wellness-records"
            resp = requests.get(url, headers=headers, timeout=10)

            if resp.status_code == 200:
                body = resp.json()
                if body.get("success") and body.get("data"):
                    return body["data"]
            elif resp.status_code == 401:
                logger.error("JWT authentication failed when fetching records")
            else:
                logger.error(f"Backend returned {resp.status_code}: {resp.text[:200]}")

            return []
        except requests.RequestException as e:
            logger.error(f"Failed to connect to backend: {e}")
            return []

    def _analyze_sleep(self, records: List[Dict]) -> Dict[str, Any]:
        """Analyze sleep patterns from records."""
        sleep_values = [
            r["sleepHours"] for r in records
            if r.get("sleepHours") is not None
        ]

        if not sleep_values:
            return {
                "averageHours": 0,
                "deficit": False,
                "consistency": "unknown",
                "daysWithData": 0
            }

        avg = sum(sleep_values) / len(sleep_values)
        deficit = avg < 7.0

        # Consistency: measure variance
        if len(sleep_values) >= 2:
            variance = sum((s - avg) ** 2 for s in sleep_values) / len(sleep_values)
            std_dev = variance ** 0.5
            if std_dev < 1.0:
                consistency = "good"
            elif std_dev < 2.0:
                consistency = "fair"
            else:
                consistency = "poor"
        else:
            consistency = "unknown"

        return {
            "averageHours": round(avg, 1),
            "deficit": deficit,
            "consistency": consistency,
            "daysWithData": len(sleep_values)
        }

    def _analyze_activity(self, records: List[Dict]) -> Dict[str, Any]:
        """Analyze activity patterns from records."""
        active_records = [
            r for r in records
            if r.get("activityDurationMinutes") is not None
            and r.get("activityDurationMinutes", 0) > 0
        ]

        total_duration = sum(
            r.get("activityDurationMinutes", 0) for r in active_records
        )

        total_days = len(records)
        avg_per_day = total_duration / total_days if total_days > 0 else 0

        # Frequency — days with any activity
        active_days = len(active_records)
        freq_per_week = (active_days / total_days) * 7 if total_days > 0 else 0

        # Trend — compare last 7 days vs previous 7 days
        trend = self._calculate_trend(records)

        return {
            "averageMinutesPerDay": round(avg_per_day, 1),
            "frequencyPerWeek": round(freq_per_week, 1),
            "activeDays": active_days,
            "totalDays": total_days,
            "trend": trend
        }

    def _calculate_trend(self, records: List[Dict]) -> str:
        """Calculate activity trend: increasing, stable, or declining."""
        if len(records) < 4:
            return "insufficient_data"

        # Sort by date
        sorted_records = sorted(records, key=lambda r: r.get("recordDate", ""))

        # Split into two halves
        mid = len(sorted_records) // 2
        first_half = sorted_records[:mid]
        second_half = sorted_records[mid:]

        first_avg = sum(
            r.get("activityDurationMinutes", 0) or 0 for r in first_half
        ) / len(first_half)
        second_avg = sum(
            r.get("activityDurationMinutes", 0) or 0 for r in second_half
        ) / len(second_half)

        diff = second_avg - first_avg
        threshold = 5  # minutes difference threshold

        if diff > threshold:
            return "increasing"
        elif diff < -threshold:
            return "declining"
        else:
            return "stable"

    def _generate_recommendations(
        self,
        records: List[Dict],
        sleep_analysis: Dict,
        activity_analysis: Dict
    ) -> List[str]:
        """Generate personalized recommendations based on analysis."""
        recommendations = []

        # Sleep recommendations
        avg_sleep = sleep_analysis.get("averageHours", 0)
        if sleep_analysis.get("deficit"):
            recommendations.append(
                f"Your average sleep is {avg_sleep}h, below the recommended "
                f"7-9 hours. Try going to bed 30 minutes earlier and avoiding "
                f"screens before bedtime to improve sleep duration."
            )
        elif avg_sleep > 0:
            recommendations.append(
                f"Great job! Your average sleep of {avg_sleep}h is within "
                f"the healthy range. Keep maintaining your consistent sleep schedule."
            )

        if sleep_analysis.get("consistency") in ("poor", "fair"):
            recommendations.append(
                "Your sleep schedule varies quite a bit. Try to go to bed "
                "and wake up at the same time each day — even on weekends — "
                "to improve sleep quality."
            )

        # Activity recommendations
        trend = activity_analysis.get("trend", "insufficient_data")
        freq = activity_analysis.get("frequencyPerWeek", 0)

        if trend == "declining":
            recommendations.append(
                "Your activity level has been declining recently. "
                "Try scheduling short 15-20 minute walks or workouts to "
                "rebuild the habit. Consistency matters more than intensity!"
            )
        elif trend == "increasing":
            recommendations.append(
                "Your activity level is trending upward — keep up the momentum! "
                "Consider setting a new goal to maintain your progress."
            )

        if freq < 3:
            recommendations.append(
                f"You're active about {freq:.0f} days per week. "
                f"The WHO recommends at least 150 minutes of moderate activity "
                f"per week. Try adding one more active day to your routine."
            )

        # General wellness
        if len(recommendations) < 3:
            recommendations.append(
                "Remember to stay hydrated! Aim for 2-3 liters of water daily, "
                "especially on days when you exercise."
            )

        if len(recommendations) < 3:
            recommendations.append(
                "Consider adding mindfulness or meditation to your routine. "
                "Just 5-10 minutes a day can help reduce stress and improve focus."
            )

        return recommendations[:4]  # Cap at 4 recommendations

    def _build_summary(
        self,
        sleep_analysis: Dict,
        activity_analysis: Dict,
        record_count: int
    ) -> str:
        """Build a human-readable analysis summary."""
        parts = [f"Analyzed {record_count} days of wellness data."]

        avg_sleep = sleep_analysis.get("averageHours", 0)
        if avg_sleep > 0:
            parts.append(
                f"Average sleep: {avg_sleep}h "
                f"({'below' if sleep_analysis.get('deficit') else 'within'} "
                f"recommended range)."
            )

        avg_activity = activity_analysis.get("averageMinutesPerDay", 0)
        trend = activity_analysis.get("trend", "")
        if avg_activity > 0:
            parts.append(
                f"Average daily activity: {avg_activity}min "
                f"(trend: {trend})."
            )

        return " ".join(parts)
