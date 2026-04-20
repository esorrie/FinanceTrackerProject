import React, { useState, useRef, useEffect } from "react";
import { NavLink } from "react-router-dom";
import '../css/NavBar.css';
import logo from "./Images/SpendX logo 3.png";
import { useUser } from "../UserContext";

const NavBar = () => {
    const { user } = useUser();
    const [query, setQuery] = useState("");
    const [assets, setAssets] = useState([]);
    const [assetsLoading, setAssetsLoading] = useState(false);
    const [assetLoadError, setAssetLoadError] = useState("");
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [addingSymbol, setAddingSymbol] = useState("");
    const searchRef = useRef(null);
    const normalizedQuery = query.trim().toLowerCase();

    const loadAssets = async (signal) => {
        setAssetsLoading(true);
        setAssetLoadError("");

        try {
            const response = await fetch('/api/assets', signal ? { signal } : undefined);
            if (!response.ok) {
                throw new Error('Failed to fetch assets');
            }

            const data = await response.json();
            setAssets(Array.isArray(data) ? data : []);
        } catch (error) {
            if (error && error.name === 'AbortError') {
                return;
            }
            setAssets([]);
            setAssetLoadError("Could not load stocks. Make sure backend API is running on port 8080.");
        } finally {
            if (!signal || !signal.aborted) {
                setAssetsLoading(false);
            }
        }
    };

    useEffect(() => {
        const controller = new AbortController();
        loadAssets(controller.signal);
        return () => controller.abort();
    }, []);

    const suggestions = normalizedQuery
        ? assets
            .filter(asset => {
                const name = (asset.assetName || '').toLowerCase();
                const symbol = (asset.assetSymbol || '').toLowerCase();
                return name.startsWith(normalizedQuery) || symbol.startsWith(normalizedQuery);
            })
            .slice(0, 8)
        : [];

    const handleSearch = (e) => {
        e.preventDefault();
        setShowSuggestions(false);
        console.log("Search submitted:", query);
    };

    const handleSearchInputChange = (event) => {
        const nextValue = event.target.value;
        const trimmedValue = nextValue.trim();
        setQuery(nextValue);
        setShowSuggestions(Boolean(trimmedValue));

        if (trimmedValue && assets.length === 0 && !assetsLoading) {
            loadAssets();
        }
    };

    const handleSuggestionSelect = (asset) => {
        const nextQuery = asset.assetName || asset.assetSymbol || '';
        setQuery(nextQuery);
    };

    const addToHoldings = async (asset) => {
        const symbol = asset?.assetSymbol;
        if (!symbol) {
            alert('Unable to add holding: missing stock symbol');
            return;
        }

        const username = user?.username || 'demo';

        const unitsRaw = window.prompt(`Enter units for ${symbol} (e.g. 1.5):`, '1');
        if (!unitsRaw) return;
        const units = Number(unitsRaw);
        if (Number.isNaN(units) || units <= 0) {
            alert('Invalid units');
            return;
        }

        const avgPriceRaw = window.prompt(`Enter average purchase price for ${symbol} (e.g. 123.45):`, '1');
        if (!avgPriceRaw) return;
        const avgPurchasePrice = Number(avgPriceRaw);
        if (Number.isNaN(avgPurchasePrice) || avgPurchasePrice <= 0) {
            alert('Invalid average purchase price');
            return;
        }

        const portfolioName = window.prompt('Enter portfolio name (or leave blank for default):', 'Portfolio');

        const today = new Date().toISOString().slice(0, 10);
        const purchaseDateRaw = window.prompt(`Enter purchase date for ${symbol} (YYYY-MM-DD):`, today);
        if (!purchaseDateRaw) return;
        const purchaseDate = purchaseDateRaw.trim();
        const isDatePatternValid = /^\d{4}-\d{2}-\d{2}$/.test(purchaseDate);
        if (!isDatePatternValid) {
            alert('Invalid date format. Use YYYY-MM-DD.');
            return;
        }
        const parsedDate = new Date(`${purchaseDate}T00:00:00Z`);
        const isCalendarDateValid = !Number.isNaN(parsedDate.getTime())
            && parsedDate.toISOString().slice(0, 10) === purchaseDate;
        if (!isCalendarDateValid) {
            alert('Invalid calendar date.');
            return;
        }
        if (parsedDate > new Date()) {
            alert('Purchase date cannot be in the future.');
            return;
        }

        try {
            setAddingSymbol(symbol);

            const response = await fetch('/api/holdings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    symbol,
                    units,
                    avgPurchasePrice,
                    portfolioName: portfolioName || undefined,
                    purchaseDate
                })
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || 'Failed to create holding');
            }

            await response.json();
            alert(`Added ${symbol} to holdings`);
            setShowSuggestions(false);
        } catch (error) {
            alert(`Error creating holding: ${error?.message || error}`);
        } finally {
            setAddingSymbol('');
        }
    };

    // Close dropdowns when clicking outside
    useEffect(() => {
        function handleClickOutside(event) {
            if (showSuggestions && searchRef.current && !searchRef.current.contains(event.target)) {
                setShowSuggestions(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [showSuggestions]);

    return (
        <>
            <div className="navBarMain">
                <div className="navBarTitle">
                    <NavLink to="/">
                        <img src={logo} alt="Finance Tracker Logo" className="navBarLogo" />
                    </NavLink>
                </div>

                <div className="navBarContent">
                    <div className="navBarContentEnd">
                        <div ref={searchRef} className="navBarSearchWrapper">
                            <form className="navBarSearch" onSubmit={handleSearch}>
                                <input
                                    type="search"
                                    placeholder="Search stocks..."
                                    value={query}
                                    onChange={handleSearchInputChange}
                                    onFocus={() => {
                                        setShowSuggestions(Boolean(normalizedQuery));
                                        if (assets.length === 0 && !assetsLoading) {
                                            loadAssets();
                                        }
                                    }}
                                    onKeyDown={(event) => {
                                        if (event.key === 'Escape') {
                                            setShowSuggestions(false);
                                        }
                                    }}
                                    aria-label="Search stocks"
                                    aria-autocomplete="list"
                                    aria-expanded={showSuggestions && normalizedQuery.length > 0}
                                    className="navBarSearchInput"
                                />
                            </form>

                            {showSuggestions && normalizedQuery && (
                                <div className="navBarSearchSuggestions" role="listbox" aria-label="Matching stocks">
                                    {assetsLoading ? (
                                        <div className="navBarSearchSuggestionStatus">Loading stocks...</div>
                                    ) : assetLoadError ? (
                                        <div className="navBarSearchSuggestionStatus">{assetLoadError}</div>
                                    ) : suggestions.length > 0 ? (
                                        suggestions.map((asset, index) => (
                                            <div
                                                key={`${asset.assetId ?? 'stock'}-${asset.assetSymbol ?? asset.assetName ?? 'item'}-${index}`}
                                                className="navBarSearchSuggestionRow"
                                            >
                                                <button
                                                    type="button"
                                                    className="navBarSearchSuggestion"
                                                    onClick={() => handleSuggestionSelect(asset)}
                                                >
                                                    <span className="navBarSearchSuggestionName">{asset.assetName || 'Unknown stock'}</span>
                                                    <span className="navBarSearchSuggestionSymbol">{asset.assetSymbol || '-'}</span>
                                                </button>
                                                <button
                                                    type="button"
                                                    className="navBarSearchAddButton"
                                                    onClick={() => addToHoldings(asset)}
                                                    disabled={addingSymbol === asset.assetSymbol}
                                                >
                                                    {addingSymbol === asset.assetSymbol ? 'Adding...' : 'Add'}
                                                </button>
                                            </div>
                                        ))
                                    ) : (
                                        <div className="navBarSearchSuggestionStatus">No stocks found</div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
};

export default NavBar;
