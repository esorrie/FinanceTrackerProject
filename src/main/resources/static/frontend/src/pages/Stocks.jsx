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
                    {!loading && !error && (
                        <table className="stocksTable">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Symbol</th>
                                    <th>Name</th>
                                    <th>Currency</th>
                                </tr>
                            </thead>
                            <tbody>
                                {assets.map(a => (
                                    <tr key={a.assetId}>
                                        <td>{a.assetId}</td>
                                        <td>{a.assetSymbol}</td>
                                        <td>{a.assetName}</td>
                                        <td>{a.currencyCode}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>
        </>
    )
}

export default Stocks;