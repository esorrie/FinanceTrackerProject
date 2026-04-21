import React from "react";
import PortfolioHoldingRows from "./PortfolioHoldingRows";
import PortfolioRangeControls from "./PortfolioRangeControls";
import PortfolioTimeline from "./PortfolioTimeline";
import {
    CHART_HEIGHT,
    CHART_PADDING_X,
    CHART_PADDING_Y,
    CHART_WIDTH,
    formatChartAxisValue
} from "../utils/portfolioUtils";

const PortfolioDataPanel = ({
    chartModel,
    handleApplyPortfolioCustomRange,
    handlePortfolioRangeClick,
    historyError,
    isHistoryLoading,
    isPortfolioCustomInputOpen,
    portfolioActiveRange,
    portfolioActiveRangeLabel,
    portfolioCustomRangeError,
    portfolioCustomRangeInput,
    portfolioHoldingRows,
    portfolioSummary,
    portfolioTimelineTicks,
    setPortfolioCustomRangeInput,
    yAxisTicks
}) => (
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

                <PortfolioTimeline ticks={portfolioTimelineTicks} keyPrefix="portfolio-timeline" />

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

                <div className="portfolioDataScrollSection">
                    <PortfolioHoldingRows rows={portfolioHoldingRows} />
                </div>
            </>
        )}
    </>
);

export default PortfolioDataPanel;
