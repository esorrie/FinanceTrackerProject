import React from "react";
import PortfolioDataPanel from "../Components/PortfolioDataPanel";
import PortfolioRangeControls from "../Components/PortfolioRangeControls";
import SelectedAssetPanel from "../Components/SelectedAssetPanel";
import "../css/Portfolio.css";
import usePortfolioViewModel from "../hooks/usePortfolioViewModel";
import {
    CHART_HEIGHT,
    CHART_PADDING_X,
    CHART_PADDING_Y,
    CHART_WIDTH,
    formatChartAxisValue,
    formatCurrencyAmount,
    formatNumber,
    formatSignedCurrencyAmount,
    formatSignedPercent
} from "../utils/portfolioUtils";

const Portfolio = () => {
    const {
        username,
        selectedCurrency, setSelectedCurrency, currencyOptions,
        activeCurrencyCode, activeCurrencySymbol,
        holdings, sortedHoldings, portfolioSummary, portfolioHoldingRows, allocationItems,
        isLoading, error,
        isHistoryLoading, historyError,
        chartModel, yAxisTicks, portfolioTimelineTicks, graphDateRange,
        portfolioActiveRange, portfolioActiveRangeLabel,
        isPortfolioCustomInputOpen, portfolioCustomRangeInput, setPortfolioCustomRangeInput,
        portfolioCustomRangeError, handlePortfolioRangeClick, handleApplyPortfolioCustomRange,
        timeframePerformance, performanceArrow, performanceTrendClass,
        selectedHoldingSymbol, setSelectedHoldingSymbol,
        selectedHoldingHistoryMeta, selectedHoldingHistoryError, isSelectedHoldingHistoryLoading,
        selectedHoldingChartModel, selectedHoldingYAxisTicks, selectedHoldingTimelineTicks,
        selectedHoldingIdentity, selectedHoldingTradePrice, selectedHoldingSnapshot,
        stockActiveRange, isStockCustomInputOpen,
        stockCustomRangeInput, setStockCustomRangeInput,
        stockCustomRangeError, handleStockRangeClick, handleApplyStockCustomRange
    } = usePortfolioViewModel();

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

                                        <PortfolioRangeControls
                                            ariaLabel="Portfolio history range"
                                            activeRange={portfolioActiveRange}
                                            isCustomInputOpen={isPortfolioCustomInputOpen}
                                            customRangeInput={portfolioCustomRangeInput}
                                            customRangeError={portfolioCustomRangeError}
                                            onRangeClick={handlePortfolioRangeClick}
                                            onCustomInputChange={setPortfolioCustomRangeInput}
                                            onApplyCustomRange={handleApplyPortfolioCustomRange}
                                        />
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
                            <PortfolioDataPanel
                                chartModel={chartModel}
                                handleApplyPortfolioCustomRange={handleApplyPortfolioCustomRange}
                                handlePortfolioRangeClick={handlePortfolioRangeClick}
                                historyError={historyError}
                                isHistoryLoading={isHistoryLoading}
                                isPortfolioCustomInputOpen={isPortfolioCustomInputOpen}
                                portfolioActiveRange={portfolioActiveRange}
                                portfolioActiveRangeLabel={portfolioActiveRangeLabel}
                                portfolioCustomRangeError={portfolioCustomRangeError}
                                portfolioCustomRangeInput={portfolioCustomRangeInput}
                                portfolioHoldingRows={portfolioHoldingRows}
                                portfolioSummary={portfolioSummary}
                                portfolioTimelineTicks={portfolioTimelineTicks}
                                setPortfolioCustomRangeInput={setPortfolioCustomRangeInput}
                                yAxisTicks={yAxisTicks}
                            />
                        ) : (
                            <SelectedAssetPanel
                                isSelectedHoldingHistoryLoading={isSelectedHoldingHistoryLoading}
                                onApplyStockCustomRange={handleApplyStockCustomRange}
                                onCustomInputChange={setStockCustomRangeInput}
                                onRangeClick={handleStockRangeClick}
                                onShowPortfolioGraph={() => setSelectedHoldingSymbol("")}
                                rangeError={stockCustomRangeError}
                                rangeInput={stockCustomRangeInput}
                                selectedHoldingChartModel={selectedHoldingChartModel}
                                selectedHoldingHistoryError={selectedHoldingHistoryError}
                                selectedHoldingHistoryMeta={selectedHoldingHistoryMeta}
                                selectedHoldingIdentity={selectedHoldingIdentity}
                                selectedHoldingSymbol={selectedHoldingSymbol}
                                selectedHoldingTimelineTicks={selectedHoldingTimelineTicks}
                                selectedHoldingTradePrice={selectedHoldingTradePrice}
                                selectedHoldingYAxisTicks={selectedHoldingYAxisTicks}
                                selectedHoldingSnapshot={selectedHoldingSnapshot}
                                stockActiveRange={stockActiveRange}
                                stockCustomInputOpen={isStockCustomInputOpen}
                            />
                        )}
                    </div>
                </div>
            </div>
        </>
    );
};

export default Portfolio;
