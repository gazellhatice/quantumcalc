package com.quantumcalc.gateway;

import java.util.List;

/** Tüm endpoint'ler için tek JSON gövdesi; her işlem ihtiyacı olan alanları okur. */
public record CalcRequest(Double a, Double b, Double value, Integer n, Integer r,
                          List<Double> values, Boolean sample) {
}
