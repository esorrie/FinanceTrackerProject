import React, { useState, useRef, useEffect } from "react";
import { NavLink, useLocation } from "react-router-dom";
import '../css/NavBar.css';
import logo from "./Images/SpendX logo 3.png";

const NavBar = () => {
    const [query, setQuery] = useState("");
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef(null);
    const location = useLocation();
    const currentPath = (location && location.pathname ? location.pathname.toLowerCase() : '/');

    const handleSearch = (e) => {
        e.preventDefault();
        console.log("Search submitted:", query);
    }

    // Close dropdown when clicking outside
    useEffect(() => {
        function handleClickOutside(event) {
            if (dropdownOpen && dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setDropdownOpen(false);
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [dropdownOpen]);

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
                        <form className="navBarSearch" onSubmit={handleSearch}>
                            <input
                                type="search"
                                placeholder="Search..."
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                aria-label="Search"
                                className="navBarSearchInput"
                            />
                        </form>
                    </div>
                </div>
            </div>
        </>
    )
}

export default NavBar;