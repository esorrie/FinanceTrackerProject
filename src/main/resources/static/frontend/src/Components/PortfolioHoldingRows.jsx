import React from "react";
import {
    formatCurrencyAmount,
    formatSignedCurrencyAmount,
    formatSignedPercent,
    formatUnits,
    getTrendClass
} from "../utils/portfolioUtils";

const PortfolioHoldingRows = ({ rows }) => {
    if (!rows.length) return null;

    return (
        <div className="holdingSnapshotGrid portfolioHoldingsRows" aria-label="Portfolio holdings summary">
            {rows.map((row) => {
                const changeTrendClass = getTrendClass(row.totalChange);

                return (
                    <div className="portfolioHoldingDataRow" key={`${row.symbol}-${row.assetName}`}>
                        <div className="holdingSnapshotCard portfolioHoldingDataCell">
                            <div className="holdingSnapshotLabel">Name</div>
                            <div className="holdingSnapshotValue">{row.assetName}</div>
                            <div className="portfolioHoldingSymbol">{row.symbol}</div>
                        </div>

                        <div className="holdingSnapshotCard portfolioHoldingDataCell">
                            <div className="holdingSnapshotLabel">Total value</div>
                            <div className="holdingSnapshotValue">
                                {formatCurrencyAmount(row.totalValue, row.currencyCode)}
                            </div>
                        </div>

                        <div className="holdingSnapshotCard portfolioHoldingDataCell">
                            <div className="holdingSnapshotLabel">Change</div>
                            <div className={`holdingSnapshotValue holdingSnapshotValue${changeTrendClass}`}>
                                {formatSignedCurrencyAmount(row.totalChange, row.currencyCode)}
                            </div>
                            <div className={`holdingSnapshotSubValue holdingSnapshotValue${changeTrendClass}`}>
                                {formatSignedPercent(row.changePercent)}
                            </div>
                        </div>

                        <div className="holdingSnapshotCard portfolioHoldingDataCell">
                            <div className="holdingSnapshotLabel">Number of shares</div>
                            <div className="holdingSnapshotValue">{formatUnits(row.totalUnits)}</div>
                        </div>

                        <div className="holdingSnapshotCard portfolioHoldingDataCell">
                            <div className="holdingSnapshotLabel">Average price</div>
                            <div className="holdingSnapshotValue">
                                {formatCurrencyAmount(row.averagePrice, row.currencyCode)}
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

export default PortfolioHoldingRows;
