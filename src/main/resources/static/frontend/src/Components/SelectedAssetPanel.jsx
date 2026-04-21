import React from "react";
import PortfolioRangeControls from "./PortfolioRangeControls";
import PortfolioTimeline from "./PortfolioTimeline";
import SelectedHoldingSnapshot from "./SelectedHoldingSnapshot";
import {
    CHART_HEIGHT,
    CHART_PADDING_X,
    CHART_PADDING_Y,
    CHART_WIDTH,
    formatChartAxisValue,
    formatCurrencyAmount
} from "../utils/portfolioUtils";

const SelectedAssetPanel = ({
    isSelectedHoldingHistoryLoading,
    onApplyStockCustomRange,
    onCustomInputChange,
    onRangeClick,
    onShowPortfolioGraph,
    rangeError,
    rangeInput,
    selectedHoldingChartModel,
    selectedHoldingHistoryError,
    selectedHoldingHistoryMeta,
    selectedHoldingIdentity,
    selectedHoldingSymbol,
    selectedHoldingTimelineTicks,
    selectedHoldingTradePrice,
    selectedHoldingYAxisTicks,
    selectedHoldingSnapshot,
    stockActiveRange,
    stockCustomInputOpen
}) => (
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
                    onClick={onShowPortfolioGraph}
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

                <PortfolioTimeline ticks={selectedHoldingTimelineTicks} keyPrefix="selected-timeline" />

                <SelectedHoldingSnapshot snapshot={selectedHoldingSnapshot} />

                <PortfolioRangeControls
                    ariaLabel="Stock history range"
                    activeRange={stockActiveRange}
                    isCustomInputOpen={stockCustomInputOpen}
                    customRangeInput={rangeInput}
                    customRangeError={rangeError}
                    onRangeClick={onRangeClick}
                    onCustomInputChange={onCustomInputChange}
                    onApplyCustomRange={onApplyStockCustomRange}
                />
            </>
        )}
    </>
);

export default SelectedAssetPanel;
