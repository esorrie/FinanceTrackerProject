import React, { useEffect, useState } from "react";
import "../css/Stocks.css";

const Stocks = () => {
    const [assets, setAssets] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let mounted = true;
        setLoading(true);
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
            })
            .finally(() => { if (mounted) setLoading(false); });

        return () => { mounted = false; };
    }, []);

    return (
        <>
            <div className="stocksMainContainer">
                <div className="stocksListContainer">
                    {/* {!loading && !error && ( */}
                        <div className="stocksTable">
                            <div className="stocksTableHeader">
                                <div className="stocksTableColumnName">Name</div>
                                <div className="stocksTableColumnSymbol">Symbol</div>
                                <div className="stocksTableColumnCurrentPrice">Current Price</div>
                                <div className="stocksTableColumnChange">Change</div>
                                <div className="stocksTableColumnExchange">Exchange</div>
                            </div>
                            {/* <div>
                                {assets.map(a => (
                                    <div key={a.assetId}>
                                        <div>{a.assetId}</div>
                                        <div>{a.assetSymbol}</div>
                                        <div>{a.assetName}</div>
                                        <div>{a.currencyCode}</div>
                                    </div>
                                ))}
                            </div> */}
                            <div> Asset 1 </div>
                        
                        </div>
                    {/* )} */}
                </div>
            </div>
        </>
    )
}

export default Stocks;