package com.quantumcalc.core;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Finansal hassasiyet için tüm sayılar BigDecimal / NUMERIC olarak saklanır. */
@Entity
@Table(name = "calculations")
public class CalculationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String operation;

    @Column(columnDefinition = "numeric")
    private BigDecimal operandA;

    @Column(columnDefinition = "numeric")
    private BigDecimal operandB;

    @Column(columnDefinition = "numeric")
    private BigDecimal result;

    private String userId;
    private Instant createdAt = Instant.now();

    protected CalculationRecord() {}

    public CalculationRecord(String operation, BigDecimal a, BigDecimal b, BigDecimal result, String userId) {
        this.operation = operation;
        this.operandA = a;
        this.operandB = b;
        this.result = result;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public String getOperation() { return operation; }
    public BigDecimal getResult() { return result; }
}
