export const CHART_WIDTH = 900;
export const CHART_HEIGHT = 220;
export const CHART_PADDING_X = 36;
export const CHART_PADDING_Y = 18;
export const HISTORY_RANGE_OPTIONS = ["1D", "1W", "1M", "3M", "1Y", "MAX", "OPTIONAL"];
export const CUSTOM_RANGE_PATTERN = /^(\d+)\s*([DdWwMmYy])$/;
export const PERFORMANCE_EPSILON = 0.000001;

export const fetchJson = async (url, fallbackMessage) => {
    const response = await fetch(url);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || fallbackMessage);
    }
    return response.json();
};

export const toTimestamp = (isoDate) => {
    if (!isoDate) return NaN;
    const timestamp = new Date(`${isoDate}T00:00:00`).getTime();
    return Number.isNaN(timestamp) ? NaN : timestamp;
};

export const subtractRangeFromDate = (date, amount, unit) => {
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

export const filterSeriesByRange = (series, historyBounds, activeRange, customRangeSpec) => {
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

    const filtered = series.filter((point) => point.timestamp >= startTimestamp);
    return filtered.length ? filtered : series.slice(-1);
};

export const formatDateLabel = (isoDate) => {
    const timestamp = toTimestamp(isoDate);
    if (!Number.isFinite(timestamp)) return isoDate || "";
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric"
    }).format(new Date(timestamp));
};

export const formatTimelineDateLabel = (isoDate) => {
    const timestamp = toTimestamp(isoDate);
    if (!Number.isFinite(timestamp)) return isoDate || "";
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric"
    }).format(new Date(timestamp));
};

export const createLinePath = (series, scales) => {
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

export const createTimelineTicks = (series, scales, maxTicks = 6) => {
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

export const getErrorMessage = (errorLike, fallback) => {
    if (errorLike instanceof Error && errorLike.message) return errorLike.message;
    if (typeof errorLike === "string" && errorLike.trim()) return errorLike;
    return fallback;
};

export const formatNumber = (value) => {
    const numberValue = Number(value);
    if (Number.isNaN(numberValue)) return "0.00";
    return numberValue.toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
};

export const formatChartAxisValue = (value) => {
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

export const formatCurrencyAmount = (value, currencyCode) => {
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

export const formatSignedCurrencyAmount = (value, currencyCode) => {
    const numberValue = Number(value);
    if (!Number.isFinite(numberValue)) return formatCurrencyAmount(0, currencyCode);

    const absoluteFormatted = formatCurrencyAmount(Math.abs(numberValue), currencyCode);
    if (numberValue > PERFORMANCE_EPSILON) return `+${absoluteFormatted}`;
    if (numberValue < -PERFORMANCE_EPSILON) return `-${absoluteFormatted}`;
    return absoluteFormatted;
};

export const formatSignedPercent = (value) => {
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

export const getTrendClass = (value) => {
    const numberValue = Number(value);
    if (!Number.isFinite(numberValue)) return "flat";
    if (numberValue > PERFORMANCE_EPSILON) return "up";
    if (numberValue < -PERFORMANCE_EPSILON) return "down";
    return "flat";
};

export const formatUnits = (value) => {
    const numberValue = Number(value);
    if (!Number.isFinite(numberValue)) return "0";

    return numberValue.toLocaleString(undefined, {
        minimumFractionDigits: 0,
        maximumFractionDigits: 4
    });
};

export const getCurrencySymbol = (currencyCode) => {
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
