import React from "react";
import {
    formatCurrencyAmount,
    formatSignedCurrencyAmount,
    formatSignedPercent,
    formatUnits
} from "../utils/portfolioUtils";

const SelectedHoldingSnapshot = ({ snapshot }) => {
    if (!snapshot) return null;

    return (
        <div className="holdingSnapshotGrid" aria-label="Selected stock summary">
            <div className="holdingSnapshotCard">
                <div className="holdingSnapshotLabel">Value</div>
                <div className="holdingSnapshotValue">
                    {formatCurrencyAmount(snapshot.value, snapshot.currency)}
                </div>
            </div>

            <div className="holdingSnapshotCard">
                <div className="holdingSnapshotLabel">Return</div>
                <div className={`holdingSnapshotValue holdingSnapshotValue${snapshot.trend}`}>
                    {formatSignedCurrencyAmount(snapshot.returnAmount, snapshot.currency)}
                </div>
                <div className={`holdingSnapshotSubValue holdingSnapshotValue${snapshot.trend}`}>
                    {formatSignedPercent(snapshot.returnPercent)}
                </div>
            </div>

            <div className="holdingSnapshotCard">
                <div className="holdingSnapshotLabel">Shares</div>
                <div className="holdingSnapshotValue">
                    {formatUnits(snapshot.shares)}
                </div>
            </div>

            <div className="holdingSnapshotCard">
                <div className="holdingSnapshotLabel">Average price</div>
                <div className="holdingSnapshotValue">
                    {formatCurrencyAmount(snapshot.averagePrice, snapshot.currency)}
                </div>
            </div>
        </div>
    );
};

export default SelectedHoldingSnapshot;
