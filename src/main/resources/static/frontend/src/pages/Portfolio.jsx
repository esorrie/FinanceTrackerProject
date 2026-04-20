import React, { useEffect, useMemo, useState } from "react";
import "../css/Portfolio.css";
import { useUser } from "../UserContext";

const Portfolio = () => {
    const { user } = useUser();
    const [holdings, setHoldings] = useState([]);
    const [portfolioSummary, setPortfolioSummary] = useState({
        totalMarketValueTarget: 0,
        totalUnrealizedPnlTarget: 0,
        targetCurrency: "£"
    });
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState("");

    const username = useMemo(() => user?.username || "demo", [user]);

    useEffect(() => {
        let mounted = true;

        const fetchHoldings = async () => {
            try {
                setIsLoading(true);
                setError("");

                const response = await fetch(`/api/holdings?username=${encodeURIComponent(username)}`);
                if (!response.ok) {
                    const text = await response.text();
                    throw new Error(text || "Failed to load holdings");
                }

                const data = await response.json();
                if (!mounted) return;

                setHoldings(Array.isArray(data.holdings) ? data.holdings : []);
                setPortfolioSummary({
                    totalMarketValueTarget: data.totalMarketValueTarget ?? 0,
                    totalUnrealizedPnlTarget: data.totalUnrealizedPnlTarget ?? 0,
                    targetCurrency: data.targetCurrency || "£"
                });
            } catch (err) {
                if (!mounted) return;
                setHoldings([]);
                setError(err?.message || "Unable to load holdings");
            } finally {
                if (mounted) setIsLoading(false);
            }
        };

        fetchHoldings();
        return () => {
            mounted = false;
        };
    }, [username]);

    const formatNumber = (value) => {
        const numberValue = Number(value);
        if (Number.isNaN(numberValue)) return "0.00";
        return numberValue.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    };

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

    const sortedHoldings = useMemo(() => {
        return [...holdings].sort((a, b) => Number(b.marketValueTarget || 0) - Number(a.marketValueTarget || 0));
    }, [holdings]);



    return (
        <>
            <div className="portfolioMainContainer">
                <div className="portfolioPreviewContainer">
                    <div className="portfolioValue">
                        {portfolioSummary.targetCurrency} {formatNumber(portfolioSummary.totalMarketValueTarget)}
                    </div>
                    <div className="portfolioPerformance">
                        {portfolioSummary.targetCurrency} {formatNumber(portfolioSummary.totalUnrealizedPnlTarget)}
                    </div>

                    <div className="portfolioContentContainer">
                        <div className="portfolioGraphContainer">
                            <div className="portfolioGraph">Portfolio graph</div>
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

                            {!isLoading && !error && sortedHoldings.map((holding) => (
                                <div className="portfolioAssetsList" key={holding.holdingId}>
                                    <strong>{holding.symbol}</strong> -
                                    {" "}{formatNumber(holding.marketValueTarget)} {holding.targetCurrency}
                                </div>
                            ))}
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
                                const boxWeight = Math.max(8, item.percentage);
                                const boxBasis = Math.max(24, Math.min(68, item.percentage));
                                return (
                                    <div
                                        className="portfolioAllocationList allocationBox"
                                        key={item.label}
                                        style={{
                                            flexGrow: boxWeight,
                                            flexBasis: `${boxBasis}%`
                                        }}
                                    >
                                        <div className="allocationAssetName">{index + 1}. {item.label}</div>
                                        <div className="allocationAssetPercent">{formatNumber(item.percentage)}%</div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </div>

                <div className="portfolioDataContainer">
                    <div className="portfolioData">This is where the portfolio details will be displayed.</div>
                </div>
            </div>
        </>
    );
};

export default Portfolio;