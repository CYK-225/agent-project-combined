package org.example.agent.Sp.dal.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Data
@RequiredArgsConstructor
@Component
public class SupplyPlanDoService {
    private final DailyProcurementPlanService dailyProcurementPlanService;
    private final DishPreferencesService dishPreferencesService;
    private final DishAttributesService dishAttributesService;
    private final DishService dishService;
    private final IngredientService ingredientService;
}
