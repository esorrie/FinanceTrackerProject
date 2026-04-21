import React from "react";
import { HISTORY_RANGE_OPTIONS } from "../utils/portfolioUtils";

const PortfolioRangeControls = ({
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

export default PortfolioRangeControls;
