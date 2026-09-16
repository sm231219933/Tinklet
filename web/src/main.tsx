import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import WebEnhancements from './WebEnhancements'
import './styles.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
    <WebEnhancements />
  </React.StrictMode>
)
