import React, { useEffect, useMemo, useState } from "react";
import "../css/Portfolio.css";
import { useUser } from "../UserContext";

const CHART_WIDTH = 900;
const CHART_HEIGHT = 220;
const CHART_PADDING_X = 36;
const CHART_PADDING_Y = 18;
const HISTORY_RANGE_OPTIONS = ["1D", "1W", "1M", "3M", "1Y", "MAX", "OPTIONAL"];
const CUSTOM_RANGE_PATTERN = /^(\d+)\s*([DdWwMmYy])$/;
const PERFORMANCE_EPSILON = 0.000001;

const fetchJson = async (url, fallbackMessage) => {
    const response = await fetch(url);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || fallbackMessage);
    }
    return response.json();
};

const toTimestamp = (isoDate) => {
    if (!isoDate) return NaN;
    const timestamp = new Date(`${isoDate}T00:00:00`).getTime();
    return Number.isNaN(timestamp) ? NaN : timestamp;
};

const subtractRangeFromDate = (date, amount, unit) => {
    const next = new Date(date.getTime());
    switch (unit) {
        case "D":
            next.setDate(next.getDate() - amount);
            break;
        case "W":
            next.setDate(next.getDate() - (amount * 7));
            break;
        case "M":
            next.setMonth(next.getMonth() - amount);
            break;
        case "Y":
            next.setFullYear(next.getFullYear() - amount);
            break;
        default:
            break;
    }
    return next;
};

const filterSeriesByRange = (series, historyBounds, activeRange, customRangeSpec) => {
    if (!series.length || !historyBounds) return [];

    if (activeRange === "MAX") {
        return series;
    }

    let startTimestamp = historyBounds.earliestTimestamp;
    const latestDate = new Date(historyBounds.latestTimestamp);

    if (activeRange === "CUSTOM") {
        if (!customRangeSpec) {
            return series;
        }
        startTimestamp = subtractRangeFromDate(latestDate, customRangeSpec.amount, customRangeSpec.unit).getTime();
    } else {
        switch (activeRange) {
            case "1D":
                startTimestamp = subtractRangeFromDate(latestDate, 1, "D").getTime();
                break;
            case "1W":
                startTimestamp = subtractRangeFromDate(latestDate, 1, "W").getTime();
                break;
            case "1M":
                startTimestamp = subtractRangeFromDate(latestDate, 1, "M").getTime();
                break;
            case "3M":
                startTimestamp = subtractRangeFromDate(latestDate, 3, "M").getTime();
                break;
            case "1Y":
                startTimestamp = subtractRangeFromDate(latestDate, 1, "Y").getTime();
                break;
            default:
                startTimestamp = historyBounds.earliestTimestamp;
                break;
        }
    }

    const filtered = series.filter(point => point.timestamp >= startTimestamp);
    return filtered.length ? filtered : series.slice(-1);
};

const formatDateLabel = (isoDate) => {
    const timestamp = toTimestamp(isoDate);
    if (!Number.isFinite(timestamp)) return isoDate || "";
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric"
    }).format(new Date(timestamp));
};

const formatTimelineDateLabel = (isoDate) => {
    const timestamp = toTimestamp(isoDate);
    if (!Number.isFinite(timestamp)) return isoDate || "";
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric"
    }).format(new Date(timestamp));
};

const createLinePath = (series, scales) => {
    if (!series.length || !scales) return "";

    const xRange = scales.maxTimestamp - scales.minTimestamp || 1;
    const yRange = scales.maxValue - scales.minValue || 1;
    const plotWidth = CHART_WIDTH - CHART_PADDING_X * 2;
    const plotHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;

    return series
        .map((point, index) => {
            const x = CHART_PADDING_X + ((point.timestamp - scales.minTimestamp) / xRange) * plotWidth;
            const y = CHART_PADDING_Y + (1 - ((point.value - scales.minValue) / yRange)) * plotHeight;
            return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
        })
        .join(" ");
};

const createTimelineTicks = (series, scales, maxTicks = 6) => {
    if (!series.length || !scales) return [];

    const tickCount = Math.min(maxTicks, series.length);
    const lastIndex = series.length - 1;
    const indexSet = new Set([0, lastIndex]);
    if (tickCount > 2 && lastIndex > 0) {
        for (let i = 1; i < tickCount - 1; i += 1) {
            indexSet.add(Math.round((i * lastIndex) / (tickCount - 1)));
        }
    }

    const xRange = scales.maxTimestamp - scales.minTimestamp || 1;
    const yRange = scales.maxValue - scales.minValue || 1;
    const plotWidth = CHART_WIDTH - CHART_PADDING_X * 2;
    const plotHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;

    return Array.from(indexSet)
        .sort((a, b) => a - b)
        .map((index) => {
            const point = series[index];
            const x = CHART_PADDING_X + ((point.timestamp - scales.minTimestamp) / xRange) * plotWidth;
            const y = CHART_PADDING_Y + (1 - ((point.value - scales.minValue) / yRange)) * plotHeight;
            return {
                key: `${point.date || point.timestamp}-${index}`,
                x,
                y,
                xPercent: (x / CHART_WIDTH) * 100,
                label: formatTimelineDateLabel(point.date)
            };
        });
};

const getErrorMessage = (errorLike, fallback) => {
    if (errorLike instanceof Error && errorLike.message) return errorLike.message;
    if (typeof errorLike === "string" && errorLike.trim()) return errorLike;
    return fallback;
};

const Portfolio = () => {
    const { user } = useUser();
    const [holdings, setHoldings] = useState([]);
    const [portfolioHistoryPoints, setPortfolioHistoryPoints] = useState([]);
    const [selectedCurrency, setSelectedCurrency] = useState("GBP");
    const [portfolioSummary, setPortfolioSummary] = useState({
        totalMarketValueTarget: 0,
        totalInvestedTarget: 0,
        targetCurrency: "GBP"
    });
    const [isLoading, setIsLoading] = useState(true);
    const [isHistoryLoading, setIsHistoryLoading] = useState(true);
    const [error, setError] = useState("");
    const [historyError, setHistoryError] = useState("");
    const [portfolioActiveRange, setPortfolioActiveRange] = useState("1M");
    const [isPortfolioCustomInputOpen, setIsPortfolioCustomInputOpen] = useState(false);
    const [portfolioCustomRangeInput, setPortfolioCustomRangeInput] = useState("");
    const [portfolioCustomRangeSpec, setPortfolioCustomRangeSpec] = useState(null);
    const [portfolioCustomRangeError, setPortfolioCustomRangeError] = useState("");
    const [stockActiveRange, setStockActiveRange] = useState("1M");
    const [isStockCustomInputOpen, setIsStockCustomInputOpen] = useState(false);
    const [stockCustomRangeInput, setStockCustomRangeInput] = useState("");
    const [stockCustomRangeSpec, setStockCustomRangeSpec] = useState(null);
    const [stockCustomRangeError, setStockCustomRangeError] = useState("");
    const [selectedHoldingSymbol, setSelectedHoldingSymbol] = useState("");
    const [selectedHoldingHistoryPoints, setSelectedHoldingHistoryPoints] = useState([]);
    const [selectedHoldingHistoryMeta, setSelectedHoldingHistoryMeta] = useState({
        symbol: "",
        assetName: "",
        targetCurrency: ""
    });
    const [isSelectedHoldingHistoryLoading, setIsSelectedHoldingHistoryLoading] = useState(false);
    const [selectedHoldingHistoryError, setSelectedHoldingHistoryError] = useState("");

    const username = useMemo(() => user?.username || "demo", [user]);

    useEffect(() => {
        let mounted = true;

        const fetchPortfolioData = async () => {
            setIsLoading(true);
            setIsHistoryLoading(true);
            setError("");
            setHistoryError("");
            setPortfolioActiveRange("1M");
            setIsPortfolioCustomInputOpen(false);
            setPortfolioCustomRangeInput("");
            setPortfolioCustomRangeSpec(null);
            setPortfolioCustomRangeError("");

            const encodedUser = encodeURIComponent(username);
            const holdingsRequest = fetchJson(
                `/api/holdings?username=${encodedUser}&currency=${encodeURIComponent(selectedCurrency)}`,
                "Failed to load holdings"
            );
            const portfolioHistoryRequest = fetchJson(
                `/api/holdings/history/portfolio?username=${encodedUser}&currency=${encodeURIComponent(selectedCurrency)}&interval=1d`,
                "Failed to load portfolio history"
            );
            const [holdingsResult, portfolioHistoryResult] = await Promise.allSettled([
                holdingsRequest,
                portfolioHistoryRequest
            ]);

            if (!mounted) return;

            if (holdingsResult.status === "fulfilled") {
                const data = holdingsResult.value;
                setHoldings(Array.isArray(data.holdings) ? data.holdings : []);
                setPortfolioSummary({
                    totalMarketValueTarget: data.totalMarketValueTarget ?? 0,
                    totalInvestedTarget: data.totalInvestedTarget ?? 0,
                    targetCurrency: data.targetCurrency || selectedCurrency
                });
            } else {
                setHoldings([]);
                setPortfolioSummary({
                    totalMarketValueTarget: 0,
                    totalInvestedTarget: 0,
                    targetCurrency: selectedCurrency
                });
                setError(getErrorMessage(holdingsResult.reason, "Unable to load holdings"));
            }

            if (portfolioHistoryResult.status === "fulfilled") {
                const historyData = portfolioHistoryResult.value;
                setPortfolioHistoryPoints(Array.isArray(historyData.points) ? historyData.points : []);
            } else {
                setPortfolioHistoryPoints([]);
                setHistoryError(getErrorMessage(
                    portfolioHistoryResult.reason,
                    "Unable to load portfolio history graph data."
                ));
            }

            setIsLoading(false);
            setIsHistoryLoading(false);
        };

        fetchPortfolioData();
        return () => {
            mounted = false;
        };
    }, [username, selectedCurrency]);

    useEffect(() => {
        if (!selectedHoldingSymbol) return;

        const hasSelectedHolding = holdings.some(
            (holding) => holding?.symbol === selectedHoldingSymbol
        );
        if (!hasSelectedHolding) {
            setSelectedHoldingSymbol("");
            setSelectedHoldingHistoryPoints([]);
            setSelectedHoldingHistoryMeta({
                symbol: "",
                assetName: "",
                targetCurrency: ""
            });
            setSelectedHoldingHistoryError("");
            setIsSelectedHoldingHistoryLoading(false);
        }
    }, [holdings, selectedHoldingSymbol, selectedCurrency]);

    useEffect(() => {
        let mounted = true;

        if (!selectedHoldingSymbol) {
            setSelectedHoldingHistoryPoints([]);
            setSelectedHoldingHistoryMeta({
                symbol: "",
                assetName: "",
                targetCurrency: ""
            });
            setSelectedHoldingHistoryError("");
            setIsSelectedHoldingHistoryLoading(false);
            return () => {
                mounted = false;
            };
        }

        const fetchSelectedHoldingHistory = async () => {
            setIsSelectedHoldingHistoryLoading(true);
            setSelectedHoldingHistoryError("");

            try {
                const encodedUser = encodeURIComponent(username);
                const encodedSymbol = encodeURIComponent(selectedHoldingSymbol);
                const selectedHoldingHistory = await fetchJson(
                    `/api/holdings/history/asset?username=${encodedUser}&symbol=${encodedSymbol}&currency=${encodeURIComponent(selectedCurrency)}&interval=1d`,
                    "Failed to load stock history"
                );

                if (!mounted) return;

                setSelectedHoldingHistoryPoints(Array.isArray(selectedHoldingHistory.points) ? selectedHoldingHistory.points : []);
                setSelectedHoldingHistoryMeta({
                    symbol: selectedHoldingHistory.symbol || selectedHoldingSymbol,
                    assetName: selectedHoldingHistory.assetName || selectedHoldingSymbol,
                    targetCurrency: selectedHoldingHistory.targetCurrency || selectedCurrency
                });
            } catch (errorLike) {
                if (!mounted) return;
                setSelectedHoldingHistoryPoints([]);
                setSelectedHoldingHistoryMeta({
                    symbol: selectedHoldingSymbol,
                    assetName: selectedHoldingSymbol,
                    targetCurrency: selectedCurrency
                });
                setSelectedHoldingHistoryError(
                    getErrorMessage(errorLike, "Unable to load selected stock history.")
                );
            } finally {
                if (mounted) {
                    setIsSelectedHoldingHistoryLoading(false);
                }
            }
        };

        fetchSelectedHoldingHistory();
        return () => {
            mounted = false;
        };
    }, [username, selectedCurrency, selectedHoldingSymbol]);

    const formatNumber = (value) => {
        const numberValue = Number(value);
        if (Number.isNaN(numberValue)) return "0.00";
        return numberValue.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    };

    const formatChartAxisValue = (value) => {
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) return "0";

        const absoluteValue = Math.abs(numberValue);
        if (absoluteValue >= 1000) {
            const compactValue = absoluteValue / 1000;
            const compactLabel = compactValue.toLocaleString(undefined, {
                minimumFractionDigits: 0,
                maximumFractionDigits: 1
            });
            return `${numberValue < 0 ? "-" : ""}${compactLabel}K`;
        }

        return formatNumber(numberValue);
    };

    const formatCurrencyAmount = (value, currencyCode) => {
        const numberValue = Number(value);
        if (Number.isNaN(numberValue)) return "0.00";

        try {
            return new Intl.NumberFormat(undefined, {
                style: "currency",
                currency: currencyCode || "GBP",
                currencyDisplay: "narrowSymbol",
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }).format(numberValue);
        } catch {
            return formatNumber(numberValue);
        }
    };

    const formatSignedCurrencyAmount = (value, currencyCode) => {
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) return formatCurrencyAmount(0, currencyCode);

        const absoluteFormatted = formatCurrencyAmount(Math.abs(numberValue), currencyCode);
        if (numberValue > PERFORMANCE_EPSILON) return `+${absoluteFormatted}`;
        if (numberValue < -PERFORMANCE_EPSILON) return `-${absoluteFormatted}`;
        return absoluteFormatted;
    };

    const formatSignedPercent = (value) => {
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) return "0.00%";

        const absoluteFormatted = Math.abs(numberValue).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
        if (numberValue > PERFORMANCE_EPSILON) return `+${absoluteFormatted}%`;
        if (numberValue < -PERFORMANCE_EPSILON) return `-${absoluteFormatted}%`;
        return `${absoluteFormatted}%`;
    };

    const formatUnits = (value) => {
        const numberValue = Number(value);
        if (!Number.isFinite(numberValue)) return "0";

        return numberValue.toLocaleString(undefined, {
            minimumFractionDigits: 0,
            maximumFractionDigits: 4
        });
    };

    const getCurrencySymbol = (currencyCode) => {
        try {
            const parts = new Intl.NumberFormat(undefined, {
                style: "currency",
                currency: currencyCode || "GBP",
                currencyDisplay: "narrowSymbol"
            }).formatToParts(0);
            return parts.find((part) => part.type === "currency")?.value || currencyCode;
        } catch {
            return currencyCode;
        }
    };

    const currencyOptions = ["GBP", "USD", "EUR", "JPY", "CAD", "AUD", "CHF", "CNY"];
    const activeCurrencyCode = portfolioSummary.targetCurrency || selectedCurrency;
    const activeCurrencySymbol = getCurrencySymbol(activeCurrencyCode);

    const allocationItems = useMemo(() => {
        if (!holdings.length) return [];

        const totalsByAsset = new Map();
        for (const holding of holdings) {
            const key = holding.symbol || holding.assetName || String(holding.holdingId);
            const current = totalsByAsset.get(key) || 0;
            totalsByAsset.set(key, current + Number(holding.marketValueTarget || 0));
        }

        const portfolioTotal = Array.from(totalsByAsset.values()).reduce((sum, value) => sum + value, 0);
        if (portfolioTotal <= 0) return [];

        const sorted = Array.from(totalsByAsset.entries())
            .map(([label, value]) => ({
                label,
                value,
                percentage: (value / portfolioTotal) * 100
            }))
            .sort((a, b) => b.percentage - a.percentage);

        const topFive = sorted.slice(0, 5);
        const other = sorted.slice(5);

        if (other.length > 0) {
            const otherValue = other.reduce((sum, item) => sum + item.value, 0);
            topFive.push({
                label: "Other",
                value: otherValue,
                percentage: (otherValue / portfolioTotal) * 100
            });
        }

        return topFive;
    }, [holdings]);

    const portfolioSeries = useMemo(() => (
        (Array.isArray(portfolioHistoryPoints) ? portfolioHistoryPoints : [])
            .map(point => ({
                date: point?.date,
                timestamp: toTimestamp(point?.date),
                value: Number(point?.portfolioValueTarget)
            }))
            .filter(point => Number.isFinite(point.timestamp) && Number.isFinite(point.value) && point.value >= 0)
            .sort((a, b) => a.timestamp - b.timestamp)
    ), [portfolioHistoryPoints]);

    const historyBounds = useMemo(() => {
        if (!portfolioSeries.length) return null;
        return {
            earliestTimestamp: portfolioSeries[0].timestamp,
            latestTimestamp: portfolioSeries[portfolioSeries.length - 1].timestamp
        };
    }, [portfolioSeries]);

    const maxPortfolioRangeStartLabel = useMemo(() => (
        portfolioSeries.length ? formatDateLabel(portfolioSeries[0].date) : ""
    ), [portfolioSeries]);

    const filteredPortfolioSeries = useMemo(() => {
        return filterSeriesByRange(
            portfolioSeries,
            historyBounds,
            portfolioActiveRange,
            portfolioCustomRangeSpec
        );
    }, [portfolioActiveRange, portfolioCustomRangeSpec, historyBounds, portfolioSeries]);

    const handlePortfolioRangeClick = (rangeLabel) => {
        setPortfolioCustomRangeError("");
        if (rangeLabel === "OPTIONAL") {
            setIsPortfolioCustomInputOpen(true);
            return;
        }

        setPortfolioActiveRange(rangeLabel);
        setIsPortfolioCustomInputOpen(false);
    };

    const handleApplyPortfolioCustomRange = () => {
        if (!historyBounds) return;

        const normalized = portfolioCustomRangeInput.trim().toUpperCase();
        const match = normalized.match(CUSTOM_RANGE_PATTERN);
        if (!match) {
            setPortfolioCustomRangeError("Use format like 10D, 6M, or 2Y.");
            return;
        }

        const amount = Number.parseInt(match[1], 10);
        const unit = match[2].toUpperCase();
        if (!Number.isFinite(amount) || amount <= 0) {
            setPortfolioCustomRangeError("Custom range must be greater than 0.");
            return;
        }

        const startTimestamp = subtractRangeFromDate(
            new Date(historyBounds.latestTimestamp),
            amount,
            unit
        ).getTime();

        if (startTimestamp < historyBounds.earliestTimestamp) {
            setPortfolioCustomRangeError(
                `Custom range exceeds MAX. Oldest available point starts on ${maxPortfolioRangeStartLabel}.`
            );
            return;
        }

        setPortfolioCustomRangeSpec({ amount, unit, label: `${amount}${unit}` });
        setPortfolioActiveRange("CUSTOM");
        setPortfolioCustomRangeError("");
    };

    const portfolioActiveRangeLabel = useMemo(() => {
        if (portfolioActiveRange === "CUSTOM") {
            return portfolioCustomRangeSpec?.label || "OPTIONAL";
        }
        return portfolioActiveRange;
    }, [portfolioActiveRange, portfolioCustomRangeSpec]);

    const timeframePerformance = useMemo(() => {
        if (!filteredPortfolioSeries.length) {
            return {
                changeAmount: 0,
                changePercent: 0,
                trend: "flat"
            };
        }

        const firstValue = Number(filteredPortfolioSeries[0].value || 0);
        const latestValue = Number(filteredPortfolioSeries[filteredPortfolioSeries.length - 1].value || 0);
        const changeAmount = latestValue - firstValue;
        const changePercent = Math.abs(firstValue) > PERFORMANCE_EPSILON
            ? (changeAmount / firstValue) * 100
            : 0;

        if (changeAmount > PERFORMANCE_EPSILON) {
            return { changeAmount, changePercent, trend: "up" };
        }

        if (changeAmount < -PERFORMANCE_EPSILON) {
            return { changeAmount, changePercent, trend: "down" };
        }

        return { changeAmount: 0, changePercent: 0, trend: "flat" };
    }, [filteredPortfolioSeries]);

    const performanceArrow = timeframePerformance.trend === "up"
        ? "\u25B2"
        : timeframePerformance.trend === "down"
            ? "\u25BC"
            : "\u2022";
    const performanceTrendClass = timeframePerformance.trend === "up"
        ? "up"
        : timeframePerformance.trend === "down"
            ? "down"
            : "flat";

    const chartModel = useMemo(() => {
        if (!filteredPortfolioSeries.length) return null;

        const timestamps = filteredPortfolioSeries.map(point => point.timestamp);
        let minTimestamp = Math.min(...timestamps);
        let maxTimestamp = Math.max(...timestamps);
        if (minTimestamp === maxTimestamp) {
            maxTimestamp = minTimestamp + 1;
        }

        const values = filteredPortfolioSeries.map(point => point.value);
        let minValue = Math.min(...values);
        let maxValue = Math.max(...values);
        if (minValue === maxValue) {
            minValue -= 1;
            maxValue += 1;
        } else {
            const padding = (maxValue - minValue) * 0.1;
            minValue -= padding;
            maxValue += padding;
        }

        const scales = { minTimestamp, maxTimestamp, minValue, maxValue };
        return {
            scales,
            portfolioPath: createLinePath(filteredPortfolioSeries, scales)
        };
    }, [filteredPortfolioSeries]);

    const yAxisTicks = useMemo(() => {
        if (!chartModel) return [];
        const { minValue, maxValue } = chartModel.scales;
        const plotHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;

        return Array.from({ length: 5 }, (_, index) => {
            const ratio = index / 4;
            return {
                y: CHART_PADDING_Y + ratio * plotHeight,
                value: maxValue - ratio * (maxValue - minValue)
            };
        });
    }, [chartModel]);
    const sortedHoldings = useMemo(() => {
        return [...holdings].sort((a, b) => Number(b.marketValueTarget || 0) - Number(a.marketValueTarget || 0));
    }, [holdings]);

    const selectedHoldingTradePrice = useMemo(() => {
        if (!selectedHoldingSymbol) return null;

        const selectedHolding = holdings.find(
            (holding) => holding?.symbol === selectedHoldingSymbol
        );
        if (!selectedHolding) return null;

        const tradePrice = Number(selectedHolding.lastPriceSource);
        if (!Number.isFinite(tradePrice)) return null;

        return {
            value: tradePrice,
            currencyCode: selectedHolding.sourceCurrency || selectedCurrency
        };
    }, [holdings, selectedHoldingSymbol, selectedCurrency]);

    const selectedHoldingIdentity = useMemo(() => {
        if (!selectedHoldingSymbol) return null;

        const selectedHolding = holdings.find(
            (holding) => holding?.symbol === selectedHoldingSymbol
        );

        return {
            assetName: selectedHolding?.assetName || selectedHoldingHistoryMeta.assetName || selectedHoldingSymbol,
            symbol: selectedHolding?.symbol || selectedHoldingHistoryMeta.symbol || selectedHoldingSymbol,
            stockExchange: selectedHolding?.stockExchange || "Unknown exchange"
        };
    }, [holdings, selectedHoldingSymbol, selectedHoldingHistoryMeta.assetName, selectedHoldingHistoryMeta.symbol]);

    const selectedHoldingSnapshot = useMemo(() => {
        if (!selectedHoldingSymbol) return null;

        const holding = holdings.find((item) => item?.symbol === selectedHoldingSymbol);
        if (!holding) return null;

        const marketValue = Number(holding.marketValueTarget || 0);
        const returnAmount = Number(holding.unrealizedPnlTarget || 0);
        const investedAmount = Number(holding.investedAmountTarget || 0);
        const returnPercent = Math.abs(investedAmount) > PERFORMANCE_EPSILON
            ? (returnAmount / investedAmount) * 100
            : 0;

        let trend = "flat";
        if (returnAmount > PERFORMANCE_EPSILON) {
            trend = "up";
        } else if (returnAmount < -PERFORMANCE_EPSILON) {
            trend = "down";
        }

        return {
            value: marketValue,
            returnAmount,
            returnPercent,
            shares: Number(holding.units || 0),
            averagePrice: Number(holding.avgPurchasePriceTarget || 0),
            currency: holding.targetCurrency
                || selectedHoldingHistoryMeta.targetCurrency
                || selectedCurrency,
            trend
        };
    }, [
        holdings,
        selectedHoldingSymbol,
        selectedHoldingHistoryMeta.targetCurrency,
        selectedCurrency
    ]);


    const graphDateRange = useMemo(() => {
        if (!filteredPortfolioSeries.length) {
            return { start: "", end: "" };
        }

        return {
            start: formatDateLabel(filteredPortfolioSeries[0].date),
            end: formatDateLabel(filteredPortfolioSeries[filteredPortfolioSeries.length - 1].date)
        };
    }, [filteredPortfolioSeries]);

    const portfolioTimelineTicks = useMemo(() => (
        createTimelineTicks(filteredPortfolioSeries, chartModel?.scales)
    ), [filteredPortfolioSeries, chartModel]);

    const selectedHoldingSeries = useMemo(() => (
        (Array.isArray(selectedHoldingHistoryPoints) ? selectedHoldingHistoryPoints : [])
            .map(point => ({
                date: point?.date,
                timestamp: toTimestamp(point?.date),
                value: Number(point?.holdingValueTarget)
            }))
            .filter(point => Number.isFinite(point.timestamp) && Number.isFinite(point.value) && point.value >= 0)
            .sort((a, b) => a.timestamp - b.timestamp)
    ), [selectedHoldingHistoryPoints]);

    const selectedHoldingHistoryBounds = useMemo(() => {
        if (!selectedHoldingSeries.length) return null;
        return {
            earliestTimestamp: selectedHoldingSeries[0].timestamp,
            latestTimestamp: selectedHoldingSeries[selectedHoldingSeries.length - 1].timestamp
        };
    }, [selectedHoldingSeries]);

    const maxStockRangeStartLabel = useMemo(() => (
        selectedHoldingSeries.length ? formatDateLabel(selectedHoldingSeries[0].date) : ""
    ), [selectedHoldingSeries]);

    const filteredSelectedHoldingSeries = useMemo(() => (
        filterSeriesByRange(
            selectedHoldingSeries,
            selectedHoldingHistoryBounds,
            stockActiveRange,
            stockCustomRangeSpec
        )
    ), [selectedHoldingHistoryBounds, selectedHoldingSeries, stockActiveRange, stockCustomRangeSpec]);

    const handleStockRangeClick = (rangeLabel) => {
        setStockCustomRangeError("");
        if (rangeLabel === "OPTIONAL") {
            setIsStockCustomInputOpen(true);
            return;
        }

        setStockActiveRange(rangeLabel);
        setIsStockCustomInputOpen(false);
    };

    const handleApplyStockCustomRange = () => {
        if (!selectedHoldingHistoryBounds) return;

        const normalized = stockCustomRangeInput.trim().toUpperCase();
        const match = normalized.match(CUSTOM_RANGE_PATTERN);
        if (!match) {
            setStockCustomRangeError("Use format like 10D, 6M, or 2Y.");
            return;
        }

        const amount = Number.parseInt(match[1], 10);
        const unit = match[2].toUpperCase();
        if (!Number.isFinite(amount) || amount <= 0) {
            setStockCustomRangeError("Custom range must be greater than 0.");
            return;
        }

        const startTimestamp = subtractRangeFromDate(
            new Date(selectedHoldingHistoryBounds.latestTimestamp),
            amount,
            unit
        ).getTime();

        if (startTimestamp < selectedHoldingHistoryBounds.earliestTimestamp) {
            setStockCustomRangeError(
                `Custom range exceeds MAX. Oldest available point starts on ${maxStockRangeStartLabel}.`
            );
            return;
        }

        setStockCustomRangeSpec({ amount, unit, label: `${amount}${unit}` });
        setStockActiveRange("CUSTOM");
        setStockCustomRangeError("");
    };

    const stockActiveRangeLabel = useMemo(() => {
        if (stockActiveRange === "CUSTOM") {
            return stockCustomRangeSpec?.label || "OPTIONAL";
        }
        return stockActiveRange;
    }, [stockActiveRange, stockCustomRangeSpec]);

    const selectedHoldingChartModel = useMemo(() => {
        if (!filteredSelectedHoldingSeries.length) return null;

        const timestamps = filteredSelectedHoldingSeries.map(point => point.timestamp);
        let minTimestamp = Math.min(...timestamps);
        let maxTimestamp = Math.max(...timestamps);
        if (minTimestamp === maxTimestamp) {
            maxTimestamp = minTimestamp + 1;
        }

        const values = filteredSelectedHoldingSeries.map(point => point.value);
        let minValue = Math.min(...values);
        let maxValue = Math.max(...values);
        if (minValue === maxValue) {
            minValue -= 1;
            maxValue += 1;
        } else {
            const padding = (maxValue - minValue) * 0.1;
            minValue -= padding;
            maxValue += padding;
        }

        const scales = { minTimestamp, maxTimestamp, minValue, maxValue };
        return {
            scales,
            holdingPath: createLinePath(filteredSelectedHoldingSeries, scales)
        };
    }, [filteredSelectedHoldingSeries]);

    const selectedHoldingYAxisTicks = useMemo(() => {
        if (!selectedHoldingChartModel) return [];
        const { minValue, maxValue } = selectedHoldingChartModel.scales;
        const plotHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;

        return Array.from({ length: 5 }, (_, index) => {
            const ratio = index / 4;
            return {
                y: CHART_PADDING_Y + ratio * plotHeight,
                value: maxValue - ratio * (maxValue - minValue)
            };
        });
    }, [selectedHoldingChartModel]);

    const selectedHoldingTimelineTicks = useMemo(() => (
        createTimelineTicks(filteredSelectedHoldingSeries, selectedHoldingChartModel?.scales)
    ), [filteredSelectedHoldingSeries, selectedHoldingChartModel]);

    const renderRangeControls = ({
        ariaLabel,
        activeRange,
        isCustomInputOpen,
        customRangeInput,
        customRangeError,
        onRangeClick,
        onCustomInputChange,
        onApplyCustomRange
    }) => (
        <div className="portfolioGraphControls">
            <div className="portfolioRangeButtons" role="group" aria-label={ariaLabel}>
                {HISTORY_RANGE_OPTIONS.map((rangeLabel) => {
                    const isActive = rangeLabel === "OPTIONAL"
                        ? activeRange === "CUSTOM" || isCustomInputOpen
                        : activeRange === rangeLabel;
                    return (
                        <button
                            type="button"
                            key={rangeLabel}
                            className={`portfolioRangeButton${isActive ? " active" : ""}`}
                            onClick={() => onRangeClick(rangeLabel)}
                        >
                            {rangeLabel}
                        </button>
                    );
                })}
            </div>

            {isCustomInputOpen && (
                <div className="portfolioRangeCustom">
                    <input
                        type="text"
                        value={customRangeInput}
                        onChange={(event) => onCustomInputChange(event.target.value)}
                        onKeyDown={(event) => {
                            if (event.key === "Enter") {
                                onApplyCustomRange();
                            }
                        }}
                        className="portfolioRangeCustomInput"
                        placeholder="e.g. 10D, 6M, 2Y"
                        aria-label="Custom time range"
                    />
                    <button
                        type="button"
                        className="portfolioRangeCustomApply"
                        onClick={onApplyCustomRange}
                    >
                        Apply
                    </button>
                </div>
            )}

            {customRangeError && (
                <div className="portfolioGraphNote">{customRangeError}</div>
            )}
        </div>
    );

    const showPortfolioGraphInDataPanel = !selectedHoldingSymbol;

    return (
        <>
            <div className="portfolioMainContainer">
                <div className="portfolioPreviewContainer">
                    <div className="portfolioValue">
                        <span style={{ position: "relative", display: "inline-block", marginRight: "6px" }}>
                            <span>{activeCurrencySymbol}</span>
                            <select
                                aria-label="Select portfolio currency"
                                value={selectedCurrency}
                                onChange={(event) => setSelectedCurrency(event.target.value)}
                                style={{
                                    position: "absolute",
                                    inset: 0,
                                    opacity: 0,
                                    border: "none",
                                    background: "transparent",
                                    appearance: "none",
                                    WebkitAppearance: "none",
                                    MozAppearance: "none",
                                    cursor: "pointer"
                                }}
                            >
                                {currencyOptions.map((currencyCode) => (
                                    <option key={currencyCode} value={currencyCode}>
                                        {currencyCode}
                                    </option>
                                ))}
                            </select>
                        </span>
                        {formatNumber(portfolioSummary.totalMarketValueTarget)}
                    </div>
                    <div className="portfolioPerformance">
                        <span>{activeCurrencySymbol} {formatNumber(portfolioSummary.totalInvestedTarget)}</span>
                        <span className={`portfolioPerformanceDelta ${performanceTrendClass}`}>
                            <span className="portfolioPerformanceArrow" aria-hidden="true">{performanceArrow}</span>
                            <span>{formatSignedCurrencyAmount(timeframePerformance.changeAmount, activeCurrencyCode)}</span>
                            <span>({formatSignedPercent(timeframePerformance.changePercent)})</span>
                            <span className="portfolioPerformanceRange">{portfolioActiveRangeLabel}</span>
                        </span>
                    </div>

                    <div className="portfolioContentContainer">
                        <div className="portfolioGraphContainer">
                            <div className="portfolioGraph">
                                {isHistoryLoading && (
                                    <div className="portfolioGraphStatus">Loading portfolio graph...</div>
                                )}

                                {!isHistoryLoading && historyError && (
                                    <div className="portfolioGraphStatus">{historyError}</div>
                                )}

                                {!isHistoryLoading && !historyError && !chartModel && (
                                    <div className="portfolioGraphStatus">No portfolio history is available yet.</div>
                                )}

                                {!isHistoryLoading && !historyError && chartModel && (
                                    <>
                                        <div className="portfolioGraphHeaderRow">
                                            <div className="portfolioGraphHeader">
                                                <div className="portfolioGraphTitle portfolioGraphTitlePortfolio">Portfolio history</div>
                                                <div className="portfolioGraphSubtitle portfolioGraphSubtitlePortfolio">
                                                    Value in {portfolioSummary.targetCurrency} | Range {portfolioActiveRangeLabel}
                                                </div>
                                            </div>
                                            <button
                                                type="button"
                                                className="portfolioGraphExpandButton"
                                                onClick={() => setSelectedHoldingSymbol("")}
                                                disabled={!selectedHoldingSymbol}
                                                aria-label="Show portfolio graph"
                                                title="Show portfolio graph"
                                            >
                                                <svg
                                                    viewBox="0 0 24 24"
                                                    className="portfolioGraphControlIcon"
                                                    aria-hidden="true"
                                                >
                                                    <path
                                                        d="M7 14H5v5h5v-2H7v-3Zm0-4h3V8H7V5H5v5Zm10 7h-3v2h5v-5h-2v3Zm-3-12v2h3v3h2V5h-5Z"
                                                        fill="currentColor"
                                                    />
                                                </svg>
                                            </button>
                                        </div>

                                        <div className="portfolioGraphLegend">
                                            <div className="portfolioGraphLegendItem">
                                                <span className="portfolioGraphLegendSwatch portfolioGraphLegendSwatchPortfolio"></span>
                                                Portfolio
                                            </div>
                                        </div>

                                        <svg
                                            className="portfolioGraphSvg"
                                            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                                            preserveAspectRatio="none"
                                            role="img"
                                            aria-label="Portfolio history"
                                        >
                                            {yAxisTicks.map((tick, index) => (
                                                <g key={`tick-${index}`}>
                                                    <line
                                                        x1={CHART_PADDING_X}
                                                        y1={tick.y}
                                                        x2={CHART_WIDTH - CHART_PADDING_X}
                                                        y2={tick.y}
                                                        className="portfolioGraphGridLine"
                                                    />
                                                    <text x="4" y={tick.y + 4} className="portfolioGraphAxisLabel">
                                                        {formatChartAxisValue(tick.value)}
                                                    </text>
                                                </g>
                                            ))}

                                            {chartModel.portfolioPath && (
                                                <path
                                                    d={chartModel.portfolioPath}
                                                    className="portfolioGraphLine portfolioGraphLinePortfolio"
                                                />
                                            )}
                                        </svg>

                                        <div className="portfolioGraphDates">
                                            <span>{graphDateRange.start}</span>
                                            <span>{graphDateRange.end}</span>
                                        </div>

                                        {renderRangeControls({
                                            ariaLabel: "Portfolio history range",
                                            activeRange: portfolioActiveRange,
                                            isCustomInputOpen: isPortfolioCustomInputOpen,
                                            customRangeInput: portfolioCustomRangeInput,
                                            customRangeError: portfolioCustomRangeError,
                                            onRangeClick: handlePortfolioRangeClick,
                                            onCustomInputChange: setPortfolioCustomRangeInput,
                                            onApplyCustomRange: handleApplyPortfolioCustomRange
                                        })}
                                    </>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="portfolioAssetsContainer">
                        <div className="portfolioAssetsHeader">Investments</div>
                        <div className="portfolioAssetsListContainer">
                            {isLoading && <div className="portfolioAssetsList">Loading holdings...</div>}

                            {!isLoading && error && (
                                <div className="portfolioAssetsList">{error}</div>
                            )}

                            {!isLoading && !error && holdings.length === 0 && (
                                <div className="portfolioAssetsList">
                                    No holdings found for {username}. Add assets from the Stocks page.
                                </div>
                            )}

                            {!isLoading && !error && sortedHoldings.map((holding) => {
                                const symbolValue = holding.symbol || "";
                                const symbolLabel = symbolValue || holding.assetName || "Unknown";
                                return (
                                    <div className="portfolioAssetsList" key={holding.holdingId}>
                                        <button
                                            type="button"
                                            className={`portfolioAssetSymbolButton${selectedHoldingSymbol === symbolValue ? " active" : ""}`}
                                            onClick={() => setSelectedHoldingSymbol(symbolValue)}
                                            disabled={!symbolValue}
                                        >
                                            {symbolLabel}
                                        </button>{" "}
                                        -
                                        {" "}{formatCurrencyAmount(holding.marketValueTarget, holding.targetCurrency)}
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    <div className="portfolioAllocationContainer">
                        <div className="portfolioAllocationHeader">Allocations</div>
                        <div className="portfolioAllocationListContainer">
                            {isLoading && <div className="portfolioAllocationList">Loading allocations...</div>}

                            {!isLoading && !error && allocationItems.length === 0 && (
                                <div className="portfolioAllocationList">No allocation data available.</div>
                            )}

                            {!isLoading && !error && allocationItems.map((item, index) => {
                                const boxWeight = item.percentage;
                                const boxBasis = Math.max(15, item.percentage);
                                return (
                                    <div
                                        className="portfolioAllocationList allocationBox"
                                        key={item.label}
                                        style={{
                                            flexGrow: boxWeight,
                                            flexBasis: `${boxBasis}%`
                                        }}
                                    >
                                        <div className="allocationAssetName">{item.label}</div>
                                        <div className="allocationAssetPercent">{formatNumber(item.percentage)}%</div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </div>

                <div className="portfolioDataContainer">
                    <div className="portfolioData">
                        {showPortfolioGraphInDataPanel ? (
                            <>
                                <div className="portfolioGraphHeader">
                                    <div className="portfolioGraphTitle portfolioGraphTitleSelectedAsset">Portfolio history</div>
                                    <div className="portfolioGraphSubtitle portfolioGraphSubtitleSelectedAsset">
                                        {`Value in ${portfolioSummary.targetCurrency} | Range ${portfolioActiveRangeLabel}`}
                                    </div>
                                </div>

                                {isHistoryLoading && (
                                    <div className="portfolioGraphStatus">Loading portfolio graph...</div>
                                )}

                                {!isHistoryLoading && historyError && (
                                    <div className="portfolioGraphStatus">{historyError}</div>
                                )}

                                {!isHistoryLoading && !historyError && !chartModel && (
                                    <div className="portfolioGraphStatus">No portfolio history is available yet.</div>
                                )}

                                {!isHistoryLoading && !historyError && chartModel && (
                                    <>
                                        <div className="portfolioGraphLegend">
                                            <div className="portfolioGraphLegendItem">
                                                <span className="portfolioGraphLegendSwatch portfolioGraphLegendSwatchPortfolio"></span>
                                                Portfolio
                                            </div>
                                        </div>

                                        <svg
                                            className="portfolioGraphSvg"
                                            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                                            preserveAspectRatio="none"
                                            role="img"
                                            aria-label="Expanded portfolio history"
                                        >
                                            {portfolioTimelineTicks.map((tick) => (
                                                <line
                                                    key={`portfolio-xgrid-${tick.key}`}
                                                    x1={tick.x}
                                                    y1={CHART_PADDING_Y}
                                                    x2={tick.x}
                                                    y2={CHART_HEIGHT - CHART_PADDING_Y}
                                                    className="portfolioGraphXGridLine"
                                                />
                                            ))}

                                            {yAxisTicks.map((tick, index) => (
                                                <g key={`portfolio-expanded-tick-${index}`}>
                                                    <line
                                                        x1={CHART_PADDING_X}
                                                        y1={tick.y}
                                                        x2={CHART_WIDTH - CHART_PADDING_X}
                                                        y2={tick.y}
                                                        className="portfolioGraphGridLine"
                                                    />
                                                    <text x="4" y={tick.y + 4} className="portfolioGraphAxisLabel">
                                                        {formatChartAxisValue(tick.value)}
                                                    </text>
                                                </g>
                                            ))}

                                            {chartModel.portfolioPath && (
                                                <path
                                                    d={chartModel.portfolioPath}
                                                    className="portfolioGraphLine portfolioGraphLinePortfolio"
                                                />
                                            )}
                                        </svg>

                                        <div className="portfolioGraphTimeline" aria-hidden="true">
                                            {portfolioTimelineTicks.map((tick, index) => {
                                                const edgeClass = index === 0
                                                    ? " first"
                                                    : index === portfolioTimelineTicks.length - 1
                                                        ? " last"
                                                        : "";
                                                return (
                                                    <div
                                                        key={`portfolio-timeline-${tick.key}`}
                                                        className={`portfolioGraphTimelineTick${edgeClass}`}
                                                        style={{ left: `${tick.xPercent}%` }}
                                                    >
                                                        <span className="portfolioGraphTimelineLabel">{tick.label}</span>
                                                    </div>
                                                );
                                            })}
                                        </div>

                                        {renderRangeControls({
                                            ariaLabel: "Portfolio history range",
                                            activeRange: portfolioActiveRange,
                                            isCustomInputOpen: isPortfolioCustomInputOpen,
                                            customRangeInput: portfolioCustomRangeInput,
                                            customRangeError: portfolioCustomRangeError,
                                            onRangeClick: handlePortfolioRangeClick,
                                            onCustomInputChange: setPortfolioCustomRangeInput,
                                            onApplyCustomRange: handleApplyPortfolioCustomRange
                                        })}
                                    </>
                                )}
                            </>
                        ) : (
                            <>
                                <div className="portfolioGraphHeaderRow">
                                    <div className="portfolioGraphHeader">
                                        <div className="portfolioGraphTitle portfolioGraphTitleSelectedAsset">
                                            {selectedHoldingSymbol && selectedHoldingTradePrice
                                                ? `${formatCurrencyAmount(selectedHoldingTradePrice.value, selectedHoldingTradePrice.currencyCode)}`
                                                : "Stock details"}
                                        </div>
                                        <div className="portfolioGraphSubtitle portfolioGraphSubtitleSelectedAsset">
                                            {selectedHoldingSymbol
                                                ? `${selectedHoldingIdentity?.assetName || selectedHoldingSymbol} | ${selectedHoldingIdentity?.symbol || selectedHoldingSymbol} | ${selectedHoldingIdentity?.stockExchange || "Unknown exchange"}`
                                                : "Click a stock name in Investments to load its history graph here."}
                                        </div>
                                    </div>
                                    {selectedHoldingSymbol && (
                                        <button
                                            type="button"
                                            className="portfolioGraphExpandButton"
                                            onClick={() => setSelectedHoldingSymbol("")}
                                            aria-label="Show portfolio graph"
                                            title="Show portfolio graph"
                                        >
                                            <svg
                                                viewBox="0 0 24 24"
                                                className="portfolioGraphControlIcon"
                                                aria-hidden="true"
                                            >
                                                <path
                                                    d="M7 14H5v5h5v-2H7v-3Zm0-4h3V8H7V5H5v5Zm10 7h-3v2h5v-5h-2v3Zm-3-12v2h3v3h2V5h-5Z"
                                                    fill="currentColor"
                                                />
                                            </svg>
                                        </button>
                                    )}
                                </div>

                                {!selectedHoldingSymbol && (
                                    <div className="portfolioGraphStatus">Select a stock name to view history.</div>
                                )}

                                {selectedHoldingSymbol && isSelectedHoldingHistoryLoading && (
                                    <div className="portfolioGraphStatus">Loading {selectedHoldingSymbol} history...</div>
                                )}

                                {selectedHoldingSymbol && !isSelectedHoldingHistoryLoading && selectedHoldingHistoryError && (
                                    <div className="portfolioGraphStatus">{selectedHoldingHistoryError}</div>
                                )}

                                {selectedHoldingSymbol && !isSelectedHoldingHistoryLoading && !selectedHoldingHistoryError && !selectedHoldingChartModel && (
                                    <div className="portfolioGraphStatus">No history is available for {selectedHoldingSymbol}.</div>
                                )}

                                {selectedHoldingSymbol && !isSelectedHoldingHistoryLoading && !selectedHoldingHistoryError && selectedHoldingChartModel && (
                                    <>
                                        <div className="portfolioGraphLegend">
                                            <div className="portfolioGraphLegendItem">
                                                <span className="portfolioGraphLegendSwatch portfolioGraphLegendSwatchHolding"></span>
                                                {selectedHoldingHistoryMeta.symbol || selectedHoldingSymbol}
                                            </div>
                                        </div>

                                        <svg
                                            className="portfolioGraphSvg"
                                            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                                            preserveAspectRatio="none"
                                            role="img"
                                            aria-label={`${selectedHoldingHistoryMeta.symbol || selectedHoldingSymbol} history`}
                                        >
                                            {selectedHoldingTimelineTicks.map((tick) => (
                                                <line
                                                    key={`selected-xgrid-${tick.key}`}
                                                    x1={tick.x}
                                                    y1={CHART_PADDING_Y}
                                                    x2={tick.x}
                                                    y2={CHART_HEIGHT - CHART_PADDING_Y}
                                                    className="portfolioGraphXGridLine"
                                                />
                                            ))}

                                            {selectedHoldingYAxisTicks.map((tick, index) => (
                                                <g key={`selected-tick-${index}`}>
                                                    <line
                                                        x1={CHART_PADDING_X}
                                                        y1={tick.y}
                                                        x2={CHART_WIDTH - CHART_PADDING_X}
                                                        y2={tick.y}
                                                        className="portfolioGraphGridLine"
                                                    />
                                                    <text x="4" y={tick.y + 4} className="portfolioGraphAxisLabel">
                                                        {formatChartAxisValue(tick.value)}
                                                    </text>
                                                </g>
                                            ))}

                                            {selectedHoldingChartModel.holdingPath && (
                                                <path
                                                    d={selectedHoldingChartModel.holdingPath}
                                                    className="portfolioGraphLine portfolioGraphLineHolding"
                                                />
                                            )}
                                        </svg>

                                        <div className="portfolioGraphTimeline" aria-hidden="true">
                                            {selectedHoldingTimelineTicks.map((tick, index) => {
                                                const edgeClass = index === 0
                                                    ? " first"
                                                    : index === selectedHoldingTimelineTicks.length - 1
                                                        ? " last"
                                                        : "";
                                                return (
                                                    <div
                                                        key={`selected-timeline-${tick.key}`}
                                                        className={`portfolioGraphTimelineTick${edgeClass}`}
                                                        style={{ left: `${tick.xPercent}%` }}
                                                    >
                                                        <span className="portfolioGraphTimelineLabel">{tick.label}</span>
                                                    </div>
                                                );
                                            })}
                                        </div>

                                        {selectedHoldingSnapshot && (
                                            <div className="holdingSnapshotGrid" aria-label="Selected stock summary">
                                                <div className="holdingSnapshotCard">
                                                    <div className="holdingSnapshotLabel">Value</div>
                                                    <div className="holdingSnapshotValue">
                                                        {formatCurrencyAmount(
                                                            selectedHoldingSnapshot.value,
                                                            selectedHoldingSnapshot.currency
                                                        )}
                                                    </div>
                                                </div>

                                                <div className="holdingSnapshotCard">
                                                    <div className="holdingSnapshotLabel">Return</div>
                                                    <div className={`holdingSnapshotValue holdingSnapshotValue${selectedHoldingSnapshot.trend}`}>
                                                        {formatSignedCurrencyAmount(
                                                            selectedHoldingSnapshot.returnAmount,
                                                            selectedHoldingSnapshot.currency
                                                        )}
                                                    </div>
                                                    <div className={`holdingSnapshotSubValue holdingSnapshotValue${selectedHoldingSnapshot.trend}`}>
                                                        {formatSignedPercent(selectedHoldingSnapshot.returnPercent)}
                                                    </div>
                                                </div>

                                                <div className="holdingSnapshotCard">
                                                    <div className="holdingSnapshotLabel">Shares</div>
                                                    <div className="holdingSnapshotValue">
                                                        {formatUnits(selectedHoldingSnapshot.shares)}
                                                    </div>
                                                </div>

                                                <div className="holdingSnapshotCard">
                                                    <div className="holdingSnapshotLabel">Average price</div>
                                                    <div className="holdingSnapshotValue">
                                                        {formatCurrencyAmount(
                                                            selectedHoldingSnapshot.averagePrice,
                                                            selectedHoldingSnapshot.currency
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        )}

                                        {renderRangeControls({
                                            ariaLabel: "Stock history range",
                                            activeRange: stockActiveRange,
                                            isCustomInputOpen: isStockCustomInputOpen,
                                            customRangeInput: stockCustomRangeInput,
                                            customRangeError: stockCustomRangeError,
                                            onRangeClick: handleStockRangeClick,
                                            onCustomInputChange: setStockCustomRangeInput,
                                            onApplyCustomRange: handleApplyStockCustomRange
                                        })}
                                    </>
                                )}
                            </>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
};

export default Portfolio;
