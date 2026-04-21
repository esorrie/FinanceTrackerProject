import React from "react";

const PortfolioTimeline = ({ ticks, keyPrefix }) => (
    <div className="portfolioGraphTimeline" aria-hidden="true">
        {ticks.map((tick, index) => {
            const edgeClass = index === 0
                ? " first"
                : index === ticks.length - 1
                    ? " last"
                    : "";

            return (
                <div
                    key={`${keyPrefix}-${tick.key}`}
                    className={`portfolioGraphTimelineTick${edgeClass}`}
                    style={{ left: `${tick.xPercent}%` }}
                >
                    <span className="portfolioGraphTimelineLabel">{tick.label}</span>
                </div>
            );
        })}
    </div>
);

export default PortfolioTimeline;
