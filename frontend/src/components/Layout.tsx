import { NavigationProvider } from '../context/NavigationContext'
import Navbar from './Navbar'
import MainContent from './MainContent'
import Footer from './Footer'

export default function Layout() {
    return (
        <NavigationProvider>
            <div className="min-h-screen flex flex-col bg-gradient-to-br from-background-light via-bright-turquoise-50 to-background-dark">
                <Navbar />
                <main className="flex-1 flex flex-col items-stretch justify-stretch p-3">
                    <MainContent />
                </main>
                <Footer />
            </div>
        </NavigationProvider>
    )
}
