// src/components/Footer.tsx
import React from "react";
import { REPO_URL } from "../config/demo";

const Footer: React.FC = () => {
    return (
        <footer className="bg-gradient-to-r from-bright-turquoise-400 to-primary-dark text-white py-6 shadow-inner">
            <div className="max-w-screen-xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 px-4">
                <span className="text-sm">
                    © {new Date().getFullYear()} ArchEv. All Rights Reserved.
                </span>
                <ul className="flex gap-6">
                    <li>
                        <a href={REPO_URL} target="_blank" rel="noopener noreferrer" className="hover:underline">
                            GitHub
                        </a>
                    </li>
                    <li>
                        <a href={`${REPO_URL}/blob/main/LICENSE`} target="_blank" rel="noopener noreferrer" className="hover:underline">
                            License
                        </a>
                    </li>
                    <li>
                        <a href={`${REPO_URL}/issues`} target="_blank" rel="noopener noreferrer" className="hover:underline">
                            Issues
                        </a>
                    </li>
                </ul>
            </div>
        </footer>
    );
};
export default Footer;