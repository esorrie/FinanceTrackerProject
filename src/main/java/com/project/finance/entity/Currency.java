package com.project.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tbl_currency")
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id")
    private Integer currencyId;

    @Column(name = "currency_code", nullable = false, unique = true, length = 3)
    private String currencyCode;

    @Column(name = "symbol", nullable = false, length = 3)
    private String symbol;

    @Column(name = "base_unit")
    private Boolean baseUnit = Boolean.TRUE;

    public Currency() {
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Boolean getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(Boolean baseUnit) {
        this.baseUnit = baseUnit;
    }
}
