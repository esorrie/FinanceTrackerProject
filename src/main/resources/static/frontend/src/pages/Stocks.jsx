import React, { useEffect, useState } from "react";
import "../css/Stocks.css";

const Stocks = () => {
    const [assets, setAssets] = useState([]);
    const [error, setError] = useState(null);
    const [addingId, setAddingId] = useState(null);

    const portfolioId = 1;

    useEffect(() => {
        let mounted = true;
        fetch('/api/assets')
            .then(res => {
                if (!res.ok) throw new Error('Network error');
                return res.json();
            })
            .then(data => {
                if (mounted) setAssets(data);
            })
            .catch(err => {
                if (mounted) setError(err.message || 'Failed to fetch assets');
            });

        return () => { mounted = false; };
    }, []);

    const addToPortfolio = async (assetId, assetSymbol) => {
        if (!portfolioId) {
            alert('Please select a portfolio first');
            return;
        }

        const username = window.prompt('Enter your username to associate the holding with:', 'demo');
        if (!username) return;

        const unitsRaw = window.prompt('Enter number of units (e.g. 1.5):', '1');
        if (!unitsRaw) return;
        const units = Number(unitsRaw);
        if (Number.isNaN(units) || units <= 0) {
            alert('Invalid units');
            return;
        }

        const avgPriceRaw = window.prompt('Enter average purchase price (e.g. 123.45):', '0');
        if (!avgPriceRaw) return;
        const avgPurchasePrice = Number(avgPriceRaw);
        if (Number.isNaN(avgPurchasePrice) || avgPurchasePrice <= 0) {
            alert('Invalid average purchase price');
            return;
        }

        const portfolioName = window.prompt('Enter portfolio name (or leave blank to use default):', 'Portfolio');

        try {
            setAddingId(assetId);

            const body = {
                username: username,
                symbol: assetSymbol,
                units: units,
                avgPurchasePrice: avgPurchasePrice,
                portfolioName: portfolioName || undefined
            };

            const res = await fetch('/api/holdings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            if (!res.ok) {
                const text = await res.text();
                throw new Error(text || 'Failed to create holding');
            }

            const created = await res.json();
            console.log('Created holding:', created);
            alert('Holding created for ' + assetSymbol);
        } catch (err) {
            console.error(err);
            alert('Error creating holding: ' + (err.message || err));
        } finally {
            setAddingId(null);
        }
    }

    return (
        <>
            <div className="stocksMainContainer">
                <div className="stocksListContainer">
                        <div className="stocksTable">
                            <div className="stocksTableHeader">
                                <div className="stocksTableColumnTitles">Name</div>
                                <div className="stocksTableColumnTitles">Symbol</div>
                                <div className="stocksTableColumnTitles"> Price</div>
                                <div className="stocksTableColumnTitles"> Change </div>
                                <div className="stocksTableColumnTitles"> Exchange </div>
                            </div>
                            <div>
                                {assets.map(a => (
                                    <>
                                        <div className="assetInfoContainer" key={a.assetId}>
                                            <div>{a.assetName}</div>
                                            <div>{a.assetSymbol}</div>
                                            <div>{a.price}</div>
                                            <div>{a.change}</div>
                                            <div>{a.exchange}</div>
                                        </div>
                                        <button
                                        onClick={() => addToPortfolio(a.assetId, a.assetSymbol)}
                                        disabled={addingId === a.assetId}
                                        >
                                            {addingId === a.assetId ? 'Adding...' : '+'}
                                        </button>
                                    </>
                                ))}
                            </div>
                        </div>
                </div>
            </div>
        </>
    )
}

export default Stocks;