package com.fittrack.ui;

import com.fittrack.app.AppContext;
import com.fittrack.domain.NutritionGoal;
import com.fittrack.domain.PersonalRecord;
import com.fittrack.domain.WeightEntry;
import com.fittrack.service.DashboardService;
import com.fittrack.service.NutritionGoalService;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Logged-in home: today's nutrition remaining, recent weight logs, recent PRs.
 */
public final class DashboardView {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    public ScrollPane getRoot() {
        DashboardService.HomeSnapshot snap = AppContext.dashboardService().home();
        String appName = AppContext.config().get("app.name", "Fitness Tracking System");

        Label subtitle = Theme.muted("Welcome, " + snap.body().profile().getFullName()
                + " — age " + snap.body().profile().getAge() + " (from DOB)");

        Label metrics = new Label(formatBodyLine(snap));
        metrics.setWrapText(true);
        metrics.getStyleClass().add("body-text");

        VBox nutritionSection = new VBox(8, Theme.section("Today's nutrition"));
        nutritionSection.getChildren().addAll(buildNutritionBars(snap.today()));

        Label weightBody = new Label(formatWeightLogs(snap.weightTrend()));
        weightBody.setWrapText(true);
        weightBody.getStyleClass().add("body-text");

        Label prBody = new Label(formatPrs(snap));
        prBody.setWrapText(true);
        prBody.getStyleClass().add("body-text");

        VBox page = UiSupport.page(
                appName,
                subtitle,
                metrics,
                new Separator(),
                nutritionSection,
                new Separator(),
                Theme.section("Recent weight logs"),
                weightBody,
                new Separator(),
                Theme.section("Recent personal records"),
                prBody,
                Theme.hint("Use Plans to train, Nutrition → Goals for targets, Progress for logs and adherence.")
        );
        return UiSupport.scroll(page);
    }

    private static List<Node> buildNutritionBars(NutritionGoalService.DayProgress day) {
        List<Node> nodes = new ArrayList<>();
        if (day.goal() == null) {
            nodes.add(Theme.muted("No nutrition goals yet. Open Nutrition → Goals to set targets."));
            return nodes;
        }
        NutritionGoal goal = day.goal();
        nodes.add(NutritionGoalsView.macroBar("Calories", day.consumed().getKcal(), goal.getKcal(), "kcal"));
        nodes.add(NutritionGoalsView.macroBar("Protein", day.consumed().getProteinG(), goal.getProteinG(), "g"));
        Label remaining = new Label("Remaining: %.0f kcal · %.0f g protein"
                .formatted(day.remainingKcal(), day.remainingProteinG()));
        remaining.getStyleClass().add("body-text");
        nodes.add(remaining);
        return nodes;
    }

    private static String formatWeightLogs(DashboardService.WeightTrend trend) {
        if (trend.chronological().isEmpty()) {
            return "No weight logged yet. Open Profile to add one.";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, trend.chronological().size() - 8);
        for (int i = trend.chronological().size() - 1; i >= start; i--) {
            WeightEntry entry = trend.chronological().get(i);
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("• ").append(DAY.format(entry.getMeasuredAt()))
                    .append(" — ").append("%.1f kg".formatted(entry.getWeightKg()));
            if (entry.getNote() != null && !entry.getNote().isBlank()) {
                sb.append(" (").append(entry.getNote()).append(')');
            }
        }
        if (trend.deltaKg() != null) {
            sb.append("\nΔ %.1f kg vs previous".formatted(trend.deltaKg()));
        }
        return sb.toString();
    }

    private static String formatBodyLine(DashboardService.HomeSnapshot snap) {
        if (snap.body().weightKg() == null) {
            return "No weight logged yet. Open Profile to add one. Active plans: "
                    + snap.activePlanCount();
        }
        String goalBit = "";
        if (snap.bodyGoal() != null) {
            goalBit = " · Goal %.1f kg".formatted(snap.bodyGoal().getTargetWeightKg());
        }
        return "Weight %.1f kg · BMI %.1f (%s) · TDEE ~%.0f kcal · %d plans%s"
                .formatted(
                        snap.body().weightKg(),
                        snap.body().bmi(),
                        snap.body().bmiCategory(),
                        snap.body().tdee(),
                        snap.activePlanCount(),
                        goalBit
                );
    }

    private static String formatPrs(DashboardService.HomeSnapshot snap) {
        if (snap.recentPrs().isEmpty()) {
            return "No PRs yet — finish a live workout with weighted sets.";
        }
        StringBuilder sb = new StringBuilder();
        for (PersonalRecord pr : snap.recentPrs()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("• ").append(pr.getExerciseName()).append(" — ");
            if (pr.getPrType() != null) {
                sb.append(pr.getPrType().name().replace('_', ' ').toLowerCase());
            }
            if (pr.getWeightKg() != null && pr.getReps() != null) {
                sb.append(" · ").append("%.1f × %d".formatted(pr.getWeightKg(), pr.getReps()));
            } else if (pr.getEstimated1rm() != null) {
                sb.append(" · est 1RM ").append("%.1f".formatted(pr.getEstimated1rm()));
            }
        }
        return sb.toString();
    }
}
