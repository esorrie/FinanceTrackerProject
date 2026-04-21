import { useEffect, useMemo, useState } from "react";
import { useUser } from "../UserContext";
import {
    CHART_HEIGHT,
    CHART_PADDING_X,
    CHART_PADDING_Y,
    CHART_WIDTH,
    CUSTOM_RANGE_PATTERN,
    PERFORMANCE_EPSILON,
    createLinePath,
    createTimelineTicks,
    fetchJson,
    filterSeriesByRange,
    formatDateLabel,
    getCurrencySymbol,
    getTrendClass,
    getErrorMessage,
    subtractRangeFromDate,
    toTimestamp
} from "../utils/portfolioUtils";

const CURRENCY_OPTIONS = ["GBP", "USD", "EUR", "JPY", "CAD", "AUD", "CHF", "CNY"];

const usePortfolioViewModel = () => {
    const { user, selectedPortfolioId } = useUser();
    const [holdings, setHoldings] = useState([]);
    const [portfolioHistoryPoints, setPortfolioHistoryPoints] = useState([]);
    const [selectedCurrency, setSelectedCurrency] = useState("GBP");
    const [portfolioSummary, setPortfolioSummary] = useState({
        totalMarketValueTarget: 0,
        totalInvestedTarget: 0,
        totalUnrealizedPnlTarget: 0,
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
            const portfolioQuery = selectedPortfolioId != null
                ? `&portfolioId=${encodeURIComponent(selectedPortfolioId)}`
                : "";
            const holdingsRequest = fetchJson(
                `/api/holdings?username=${encodedUser}&currency=${encodeURIComponent(selectedCurrency)}${portfolioQuery}`,
                "Failed to load holdings"
            );
            const portfolioHistoryRequest = fetchJson(
                `/api/holdings/history/portfolio?username=${encodedUser}&currency=${encodeURIComponent(selectedCurrency)}&interval=1d${portfolioQuery}`,
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
                    totalUnrealizedPnlTarget: data.totalUnrealizedPnlTarget ?? 0,
                    targetCurrency: data.targetCurrency || selectedCurrency
                });
            } else {
                setHoldings([]);
                setPortfolioSummary({
                    totalMarketValueTarget: 0,
                    totalInvestedTarget: 0,
                    totalUnrealizedPnlTarget: 0,
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
        return () => { mounted = false; };
    }, [username, selectedCurrency, selectedPortfolioId]);

    useEffect(() => {
        if (!selectedHoldingSymbol) return;

        const hasSelectedHolding = holdings.some(
            (holding) => holding?.symbol === selectedHoldingSymbol
        );
        if (!hasSelectedHolding) {
            setSelectedHoldingSymbol("");
            setSelectedHoldingHistoryPoints([]);
            setSelectedHoldingHistoryMeta({ symbol: "", assetName: "", targetCurrency: "" });
            setSelectedHoldingHistoryError("");
            setIsSelectedHoldingHistoryLoading(false);
        }
    }, [holdings, selectedHoldingSymbol, selectedCurrency]);

    useEffect(() => {
        let mounted = true;

        if (!selectedHoldingSymbol) {
            setSelectedHoldingHistoryPoints([]);
            setSelectedHoldingHistoryMeta({ symbol: "", assetName: "", targetCurrency: "" });
            setSelectedHoldingHistoryError("");
            setIsSelectedHoldingHistoryLoading(false);
            return () => { mounted = false; };
        }

        const fetchSelectedHoldingHistory = async () => {
            setIsSelectedHoldingHistoryLoading(true);
            setSelectedHoldingHistoryError("");

            try {
                const encodedUser = encodeURIComponent(username);
                const encodedSymbol = encodeURIComponent(selectedHoldingSymbol);
                const portfolioQuery = selectedPortfolioId != null
                    ? `&portfolioId=${encodeURIComponent(selectedPortfolioId)}`
                    : "";
                const selectedHoldingHistory = await fetchJson(
                    `/api/holdings/history/asset?username=${encodedUser}&symbol=${encodedSymbol}&currency=${encodeURIComponent(selectedCurrency)}&interval=1d${portfolioQuery}`,
                    "Failed to load stock history"
                );

                if (!mounted) return;

                setSelectedHoldingHistoryPoints(
                    Array.isArray(selectedHoldingHistory.points) ? selectedHoldingHistory.points : []
                );
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
                if (mounted) setIsSelectedHoldingHistoryLoading(false);
            }
        };

        fetchSelectedHoldingHistory();
        return () => { mounted = false; };
    }, [username, selectedCurrency, selectedHoldingSymbol, selectedPortfolioId]);

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

        const topFive = sorted.slice(0, 4);
        const other = sorted.slice(4);

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

    const filteredPortfolioSeries = useMemo(() => (
        filterSeriesByRange(portfolioSeries, historyBounds, portfolioActiveRange, portfolioCustomRangeSpec)
    ), [portfolioActiveRange, portfolioCustomRangeSpec, historyBounds, portfolioSeries]);

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
            new Date(historyBounds.latestTimestamp), amount, unit
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
        if (portfolioActiveRange === "CUSTOM") return portfolioCustomRangeSpec?.label || "OPTIONAL";
        return portfolioActiveRange;
    }, [portfolioActiveRange, portfolioCustomRangeSpec]);

    const portfolioReturnPerformance = useMemo(() => {
        const marketValue = Number(portfolioSummary.totalMarketValueTarget || 0);
        const investedValue = Number(portfolioSummary.totalInvestedTarget || 0);
        const unrealizedPnl = portfolioSummary.totalUnrealizedPnlTarget ?? (marketValue - investedValue);
        const changeAmount = Number(unrealizedPnl || 0);
        const changePercent = Math.abs(investedValue) > PERFORMANCE_EPSILON
            ? (changeAmount / investedValue) * 100
            : 0;

        if (changeAmount > PERFORMANCE_EPSILON) return { changeAmount, changePercent, trend: "up" };
        if (changeAmount < -PERFORMANCE_EPSILON) return { changeAmount, changePercent, trend: "down" };
        return { changeAmount: 0, changePercent: 0, trend: "flat" };
    }, [
        portfolioSummary.totalMarketValueTarget,
        portfolioSummary.totalInvestedTarget,
        portfolioSummary.totalUnrealizedPnlTarget
    ]);

    const performanceArrow = portfolioReturnPerformance.trend === "up"
        ? "\u25B2"
        : portfolioReturnPerformance.trend === "down"
            ? "\u25BC"
            : "\u2022";

    const performanceTrendClass = portfolioReturnPerformance.trend === "up"
        ? "up"
        : portfolioReturnPerformance.trend === "down"
            ? "down"
            : "flat";

    const chartModel = useMemo(() => {
        if (!filteredPortfolioSeries.length) return null;

        const timestamps = filteredPortfolioSeries.map(point => point.timestamp);
        let minTimestamp = Math.min(...timestamps);
        let maxTimestamp = Math.max(...timestamps);
        if (minTimestamp === maxTimestamp) maxTimestamp = minTimestamp + 1;

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
        return { scales, portfolioPath: createLinePath(filteredPortfolioSeries, scales) };
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

    const sortedHoldings = useMemo(() => (
        [...holdings].sort((a, b) => Number(b.marketValueTarget || 0) - Number(a.marketValueTarget || 0))
    ), [holdings]);

    const portfolioHoldingRows = useMemo(() => {
        if (!holdings.length) return [];

        const grouped = new Map();
        for (const holding of holdings) {
            const symbol = holding?.symbol || "N/A";
            const existing = grouped.get(symbol) || {
                assetName: holding?.assetName || symbol,
                symbol,
                totalValue: 0,
                totalChange: 0,
                totalUnits: 0,
                totalInvested: 0,
                currencyCode: holding?.targetCurrency || activeCurrencyCode
            };

            const units = Number(holding?.units || 0);
            const marketValue = Number(holding?.marketValueTarget || 0);
            const change = Number(holding?.unrealizedPnlTarget || 0);
            const invested = Number(
                holding?.investedAmountTarget
                ?? (Number(holding?.avgPurchasePriceTarget || 0) * units)
            );

            existing.totalUnits += Number.isFinite(units) ? units : 0;
            existing.totalValue += Number.isFinite(marketValue) ? marketValue : 0;
            existing.totalChange += Number.isFinite(change) ? change : 0;
            existing.totalInvested += Number.isFinite(invested) ? invested : 0;
            grouped.set(symbol, existing);
        }

        return Array.from(grouped.values())
            .map((item) => ({
                ...item,
                changePercent: Math.abs(item.totalInvested) > PERFORMANCE_EPSILON
                    ? (item.totalChange / item.totalInvested) * 100
                    : 0,
                averagePrice: Math.abs(item.totalUnits) > PERFORMANCE_EPSILON
                    ? item.totalInvested / item.totalUnits
                    : 0
            }))
            .sort((a, b) => b.totalValue - a.totalValue);
    }, [holdings, activeCurrencyCode]);

    const selectedHolding = useMemo(() => {
        if (!selectedHoldingSymbol) return null;
        return holdings.find((holding) => holding?.symbol === selectedHoldingSymbol) || null;
    }, [holdings, selectedHoldingSymbol]);

    const selectedHoldingTradePrice = useMemo(() => {
        if (!selectedHolding) return null;
        const tradePrice = Number(selectedHolding.lastPriceSource);
        if (!Number.isFinite(tradePrice)) return null;
        return {
            value: tradePrice,
            currencyCode: selectedHolding.sourceCurrency || selectedCurrency
        };
    }, [selectedHolding, selectedCurrency]);

    const selectedHoldingIdentity = useMemo(() => {
        if (!selectedHoldingSymbol) return null;
        return {
            assetName: selectedHolding?.assetName || selectedHoldingHistoryMeta.assetName || selectedHoldingSymbol,
            symbol: selectedHolding?.symbol || selectedHoldingHistoryMeta.symbol || selectedHoldingSymbol,
            stockExchange: selectedHolding?.stockExchange || "Unknown exchange"
        };
    }, [selectedHolding, selectedHoldingSymbol, selectedHoldingHistoryMeta.assetName, selectedHoldingHistoryMeta.symbol]);

    const selectedHoldingSnapshot = useMemo(() => {
        if (!selectedHolding) return null;

        const marketValue = Number(selectedHolding.marketValueTarget || 0);
        const returnAmount = Number(selectedHolding.unrealizedPnlTarget || 0);
        const investedAmount = Number(selectedHolding.investedAmountTarget || 0);
        const returnPercent = Math.abs(investedAmount) > PERFORMANCE_EPSILON
            ? (returnAmount / investedAmount) * 100
            : 0;

        return {
            value: marketValue,
            returnAmount,
            returnPercent,
            shares: Number(selectedHolding.units || 0),
            averagePrice: Number(selectedHolding.avgPurchasePriceTarget || 0),
            currency: selectedHolding.targetCurrency
                || selectedHoldingHistoryMeta.targetCurrency
                || selectedCurrency,
            trend: getTrendClass(returnAmount)
        };
    }, [selectedHolding, selectedHoldingHistoryMeta.targetCurrency, selectedCurrency]);

    const graphDateRange = useMemo(() => {
        if (!filteredPortfolioSeries.length) return { start: "", end: "" };
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
            selectedHoldingSeries, selectedHoldingHistoryBounds, stockActiveRange, stockCustomRangeSpec
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
            new Date(selectedHoldingHistoryBounds.latestTimestamp), amount, unit
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

    const selectedHoldingChartModel = useMemo(() => {
        if (!filteredSelectedHoldingSeries.length) return null;

        const timestamps = filteredSelectedHoldingSeries.map(point => point.timestamp);
        let minTimestamp = Math.min(...timestamps);
        let maxTimestamp = Math.max(...timestamps);
        if (minTimestamp === maxTimestamp) maxTimestamp = minTimestamp + 1;

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
        return { scales, holdingPath: createLinePath(filteredSelectedHoldingSeries, scales) };
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

    return {
        username,
        // Currency
        selectedCurrency,
        setSelectedCurrency,
        currencyOptions: CURRENCY_OPTIONS,
        activeCurrencyCode,
        activeCurrencySymbol,
        // Holdings
        holdings,
        sortedHoldings,
        portfolioSummary,
        portfolioHoldingRows,
        allocationItems,
        // Loading / error
        isLoading,
        error,
        isHistoryLoading,
        historyError,
        // Portfolio chart
        chartModel,
        yAxisTicks,
        portfolioTimelineTicks,
        graphDateRange,
        portfolioActiveRange,
        portfolioActiveRangeLabel,
        isPortfolioCustomInputOpen,
        portfolioCustomRangeInput,
        setPortfolioCustomRangeInput,
        portfolioCustomRangeError,
        handlePortfolioRangeClick,
        handleApplyPortfolioCustomRange,
        // Performance display
        portfolioReturnPerformance,
        performanceArrow,
        performanceTrendClass,
        // Selected holding
        selectedHoldingSymbol,
        setSelectedHoldingSymbol,
        selectedHoldingHistoryMeta,
        selectedHoldingHistoryError,
        isSelectedHoldingHistoryLoading,
        selectedHoldingChartModel,
        selectedHoldingYAxisTicks,
        selectedHoldingTimelineTicks,
        selectedHoldingIdentity,
        selectedHoldingTradePrice,
        selectedHoldingSnapshot,
        stockActiveRange,
        isStockCustomInputOpen,
        stockCustomRangeInput,
        setStockCustomRangeInput,
        stockCustomRangeError,
        handleStockRangeClick,
        handleApplyStockCustomRange
    };
};

export default usePortfolioViewModel;
