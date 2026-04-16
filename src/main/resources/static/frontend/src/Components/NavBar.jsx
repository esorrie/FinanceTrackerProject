import React, { useState } from "react";
import { NavLink } from "react-router-dom";
import '../css/NavBar.css';
import logo from "./Images/SpendX logo 3.png";

const NavBar = () => {
    const [query, setQuery] = useState("");

    const handleSearch = (e) => {
        e.preventDefault();
        // placeholder: will use DB-backed suggestions in future
        console.log("Search submitted:", query);
    }

    return (
        <>
            <div className="navBarMain">
                <div className="navBarTitle">
                    <NavLink to="/">
                        {/* <img src="src\main\resources\static\frontend\src\Components\Images\SpendX logo.png" alt="Finance Tracker Logo" className="navBarLogo" /> */}
                        <img src={logo} alt="Finance Tracker Logo" className="navBarLogo" />
                    </NavLink>
                </div>
                <div className="navBarContent">
                    <NavLink to="/Portfolio">
                        Portfolio
                    </NavLink>
                    <NavLink to="/Stocks">
                            Stocks
                    </NavLink>
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