import React, { useEffect, useMemo, useRef, useState } from "react";
import { NavLink } from "react-router-dom";
import '../css/NavBar.css';
import logo from "./Images/SpendX logo 3.png";
import { useUser } from "../UserContext";

const NavBar = () => {
    const { user, selectedPortfolioId, setSelectedPortfolioId } = useUser();
    const [query, setQuery] = useState("");
    const [assets, setAssets] = useState([]);
    const [portfolios, setPortfolios] = useState([]);
    const [heldUnitsBySymbol, setHeldUnitsBySymbol] = useState({});
    const [assetsLoading, setAssetsLoading] = useState(false);
    const [portfoliosLoading, setPortfoliosLoading] = useState(false);
    const [assetLoadError, setAssetLoadError] = useState("");
    const [portfolioLoadError, setPortfolioLoadError] = useState("");
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [showPortfolioMenu, setShowPortfolioMenu] = useState(false);
    const [processingSymbol, setProcessingSymbol] = useState("");
    const searchRef = useRef(null);
    const portfolioRef = useRef(null);
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

    const loadPortfolios = async (signal) => {
        const username = user?.username || 'demo';
        setPortfoliosLoading(true);
        setPortfolioLoadError("");

        try {
            const response = await fetch(
                `/api/portfolios?username=${encodeURIComponent(username)}`,
                signal ? { signal } : undefined
            );
            if (!response.ok) {
                throw new Error('Failed to fetch portfolios');
            }

            const data = await response.json();
            const nextPortfolios = Array.isArray(data?.portfolios) ? data.portfolios : [];
            setPortfolios(nextPortfolios);
            setSelectedPortfolioId((previousPortfolioId) => {
                if (!nextPortfolios.length) return null;
                const isCurrentSelectionAvailable = nextPortfolios.some(
                    (portfolio) => portfolio?.portfolioId === previousPortfolioId
                );
                return isCurrentSelectionAvailable
                    ? previousPortfolioId
                    : nextPortfolios[0]?.portfolioId ?? null;
            });
        } catch (error) {
            if (error && error.name === 'AbortError') {
                return;
            }
            setPortfolios([]);
            setPortfolioLoadError("Could not load portfolios.");
            setSelectedPortfolioId(null);
        } finally {
            if (!signal || !signal.aborted) {
                setPortfoliosLoading(false);
            }
        }
    };

    const loadUserHoldings = async (signal) => {
        const username = user?.username || 'demo';
        try {
            const portfolioQuery = selectedPortfolioId != null
                ? `&portfolioId=${encodeURIComponent(selectedPortfolioId)}`
                : '';
            const response = await fetch(
                `/api/holdings?username=${encodeURIComponent(username)}${portfolioQuery}`,
                signal ? { signal } : undefined
            );
            if (!response.ok) {
                throw new Error('Failed to fetch holdings');
            }

            const data = await response.json();
            const holdings = Array.isArray(data?.holdings) ? data.holdings : [];
            const nextHeldUnitsBySymbol = holdings.reduce((acc, holding) => {
                const symbol = (holding?.symbol || '').toUpperCase();
                if (!symbol) return acc;
                const units = Number(holding?.units || 0);
                acc[symbol] = (acc[symbol] || 0) + (Number.isFinite(units) ? units : 0);
                return acc;
            }, {});

            setHeldUnitsBySymbol(nextHeldUnitsBySymbol);
        } catch (error) {
            if (error && error.name === 'AbortError') {
                return;
            }
        }
    };

    useEffect(() => {
        const controller = new AbortController();
        loadAssets(controller.signal);
        loadPortfolios(controller.signal);
        return () => controller.abort();
    }, [user?.username, setSelectedPortfolioId]);

    useEffect(() => {
        const controller = new AbortController();
        loadUserHoldings(controller.signal);
        return () => controller.abort();
    }, [user?.username, selectedPortfolioId]);

    const suggestions = normalizedQuery
        ? assets
            .filter(asset => {
                const name = (asset.assetName || '').toLowerCase();
                const symbol = (asset.assetSymbol || '').toLowerCase();
                return name.startsWith(normalizedQuery) || symbol.startsWith(normalizedQuery);
            })
            .slice(0, 8)
        : [];

    const selectedPortfolio = useMemo(
        () => portfolios.find((portfolio) => portfolio?.portfolioId === selectedPortfolioId) || null,
        [portfolios, selectedPortfolioId]
    );

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

        const defaultPortfolioName = selectedPortfolio?.portfolioName || 'Portfolio';
        const portfolioNameInput = window.prompt(
            'Enter portfolio name (or leave blank for default):',
            defaultPortfolioName
        );
        if (portfolioNameInput === null) return;
        const portfolioName = portfolioNameInput.trim() || defaultPortfolioName;

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
            setProcessingSymbol(symbol);

            const response = await fetch('/api/holdings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    symbol,
                    units,
                    avgPurchasePrice,
                    portfolioName,
                    purchaseDate
                })
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || 'Failed to create holding');
            }

            await response.json();
            alert(`Added ${symbol} to holdings`);
            await loadUserHoldings();
            setShowSuggestions(false);
        } catch (error) {
            alert(`Error creating holding: ${error?.message || error}`);
        } finally {
            setProcessingSymbol('');
        }
    };

    const updateHolding = async (asset) => {
        const symbol = (asset?.assetSymbol || '').toUpperCase();
        if (!symbol) {
            alert('Unable to update holding: missing stock symbol');
            return;
        }

        const heldUnits = Number(heldUnitsBySymbol[symbol] || 0);
        if (!Number.isFinite(heldUnits) || heldUnits <= 0) {
            await addToHoldings(asset);
            return;
        }

        const action = window.prompt(
            `${symbol}: choose action\n1 = Add units\n2 = Remove units\n3 = Remove all units`,
            '1'
        );
        if (!action) return;

        if (action.trim() === '1') {
            await addToHoldings(asset);
            return;
        }

        const username = user?.username || 'demo';

        if (action.trim() === '3') {
            const confirmed = window.confirm(`Remove all ${formatUnits(heldUnits)} units of ${symbol}?`);
            if (!confirmed) return;

            try {
                setProcessingSymbol(symbol);
                const response = await fetch('/api/holdings/units', {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        username,
                        symbol,
                        portfolioId: selectedPortfolioId ?? undefined,
                        removeAll: true
                    })
                });

                if (!response.ok) {
                    const text = await response.text();
                    throw new Error(text || 'Failed to remove holding');
                }

                await response.json();
                alert(`Removed all units for ${symbol}`);
                await loadUserHoldings();
                setShowSuggestions(false);
            } catch (error) {
                alert(`Error updating holding: ${error?.message || error}`);
            } finally {
                setProcessingSymbol('');
            }
            return;
        }

        if (action.trim() !== '2') {
            alert('Invalid option. Use 1, 2, or 3.');
            return;
        }

        const unitsRaw = window.prompt(`Enter units to remove from ${symbol} (max ${formatUnits(heldUnits)}):`, '1');
        if (!unitsRaw) return;
        const units = Number(unitsRaw);
        if (Number.isNaN(units) || units <= 0) {
            alert('Invalid units');
            return;
        }

        try {
            setProcessingSymbol(symbol);
            const response = await fetch('/api/holdings/units', {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    symbol,
                    portfolioId: selectedPortfolioId ?? undefined,
                    units,
                    removeAll: false
                })
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || 'Failed to update holding');
            }

            const result = await response.json();
            alert(`Updated ${symbol}. Remaining units: ${formatUnits(result.remainingUnits)}`);
            await loadUserHoldings();
            setShowSuggestions(false);
        } catch (error) {
            alert(`Error updating holding: ${error?.message || error}`);
        } finally {
            setProcessingSymbol('');
        }
    };

    const formatUnits = (value) => {
        const parsed = Number(value);
        if (!Number.isFinite(parsed)) return '0';
        return parsed.toLocaleString(undefined, {
            minimumFractionDigits: 0,
            maximumFractionDigits: 4
        });
    };

    // Close dropdowns when clicking outside
    useEffect(() => {
        function handleClickOutside(event) {
            if (showSuggestions && searchRef.current && !searchRef.current.contains(event.target)) {
                setShowSuggestions(false);
            }
            if (showPortfolioMenu && portfolioRef.current && !portfolioRef.current.contains(event.target)) {
                setShowPortfolioMenu(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [showPortfolioMenu, showSuggestions]);

    return (
        <>
            <div className="navBarMain">
                <div className="navBarTitle" ref={portfolioRef}>
                    <div className="navBarBrandRow">
                        <NavLink to="/">
                            <img src={logo} alt="Finance Tracker Logo" className="navBarLogo" />
                        </NavLink>
                        <button
                            type="button"
                            className={`navPortfolioChevron${showPortfolioMenu ? ' open' : ''}`}
                            onClick={() => setShowPortfolioMenu((previous) => !previous)}
                            aria-label="Select portfolio"
                            aria-expanded={showPortfolioMenu}
                        >
                            <span aria-hidden="true">▼</span>
                        </button>
                    </div>

                    <div className={`navPortfolioMenu${showPortfolioMenu ? ' open' : ''}`}>
                        <div className="navPortfolioMenuHeader">Portfolios</div>

                        {portfoliosLoading && (
                            <div className="navPortfolioMenuStatus">Loading portfolios...</div>
                        )}

                        {!portfoliosLoading && portfolioLoadError && (
                            <div className="navPortfolioMenuStatus">{portfolioLoadError}</div>
                        )}

                        {!portfoliosLoading && !portfolioLoadError && portfolios.length === 0 && (
                            <div className="navPortfolioMenuStatus">No portfolios found.</div>
                        )}

                        {!portfoliosLoading && !portfolioLoadError && portfolios.map((portfolio) => (
                            <button
                                key={portfolio.portfolioId}
                                type="button"
                                className={`navPortfolioMenuItem${portfolio.portfolioId === selectedPortfolioId ? ' active' : ''}`}
                                onClick={() => {
                                    setSelectedPortfolioId(portfolio.portfolioId);
                                    setShowPortfolioMenu(false);
                                }}
                            >
                                {portfolio.portfolioName}
                            </button>
                        ))}

                        {!portfoliosLoading && !portfolioLoadError && selectedPortfolio && (
                            <div className="navPortfolioMenuMeta">
                                Selected: {selectedPortfolio.portfolioName}
                            </div>
                        )}
                    </div>
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
                                        suggestions.map((asset, index) => {
                                            const symbol = (asset.assetSymbol || '').toUpperCase();
                                            const heldUnits = Number(heldUnitsBySymbol[symbol] || 0);
                                            const isHeld = Number.isFinite(heldUnits) && heldUnits > 0;
                                            return (
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
                                                    onClick={() => (isHeld ? updateHolding(asset) : addToHoldings(asset))}
                                                    disabled={processingSymbol === symbol}
                                                >
                                                    {processingSymbol === symbol ? 'Working...' : (isHeld ? 'Update' : 'Add')}
                                                </button>
                                            </div>
                                            );
                                        })
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
