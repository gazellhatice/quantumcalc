package com.quantumcalc.core;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalculationRepository extends JpaRepository<CalculationRecord, Long> {
}
