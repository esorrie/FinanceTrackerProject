import React, { useState, useRef, useEffect } from "react";
import { NavLink, useLocation } from "react-router-dom";
import '../css/NavBar.css';
import logo from "./Images/SpendX logo 3.png";

const NavBar = () => {
    const [query, setQuery] = useState("");
    const [assets, setAssets] = useState([]);
    const [assetsLoading, setAssetsLoading] = useState(false);
    const [assetLoadError, setAssetLoadError] = useState("");
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const dropdownRef = useRef(null);
    const searchRef = useRef(null);
    const location = useLocation();
    const currentPath = (location && location.pathname ? location.pathname.toLowerCase() : '/');
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
        setShowSuggestions(false);
    };

    // Close dropdowns when clicking outside
    useEffect(() => {
        function handleClickOutside(event) {
            if (dropdownOpen && dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setDropdownOpen(false);
            }
            if (showSuggestions && searchRef.current && !searchRef.current.contains(event.target)) {
                setShowSuggestions(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [dropdownOpen, showSuggestions]);

    return (
        <>
            <div className="navBarMain">
                <div ref={dropdownRef} className="navBarTitle navDropdown" tabIndex="0" style={{position: 'relative'}}>
                    <NavLink
                        to="/"
                        onClick={(e) => { e.preventDefault(); setDropdownOpen(!dropdownOpen); }}
                        className="navDropdownToggle"
                        aria-haspopup="true"
                        aria-expanded={dropdownOpen}
                    >
                        <img src={logo} alt="Finance Tracker Logo" className="navBarLogo" />
                        <span className={`navDropdownChevron ${dropdownOpen ? 'open' : ''}`} aria-hidden="true">▾</span>
                    </NavLink>

                    <div className={`navDropdownMenu ${dropdownOpen ? 'open' : ''}`} aria-hidden={!dropdownOpen}>
                        {!(currentPath === '/' || currentPath === '/portfolio') && (
                            <NavLink to="/" onClick={() => setDropdownOpen(false)} className="navDropdownItem">
                                Portfolio
                            </NavLink>
                        )}

                        {currentPath !== '/stocks' && (
                            <NavLink to="/stocks" onClick={() => setDropdownOpen(false)} className="navDropdownItem">
                                Stocks
                            </NavLink>
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
                                        suggestions.map((asset, index) => (
                                            <button
                                                type="button"
                                                key={`${asset.assetId ?? 'stock'}-${asset.assetSymbol ?? asset.assetName ?? 'item'}-${index}`}
                                                className="navBarSearchSuggestion"
                                                onClick={() => handleSuggestionSelect(asset)}
                                            >
                                                <span className="navBarSearchSuggestionName">{asset.assetName || 'Unknown stock'}</span>
                                                <span className="navBarSearchSuggestionSymbol">{asset.assetSymbol || '-'}</span>
                                            </button>
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
