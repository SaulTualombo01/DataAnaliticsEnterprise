import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './shell/App';
import { createHttpGateway } from './shared/http';
import './styles.css';

// Only the explicit demo build imports fixtures. Real failures never fall back to mocks.
const gateway = import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true'
  ? (await import('./mocks/gateway')).createDemoGateway()
  : createHttpGateway();

createRoot(document.getElementById('root')!).render(<React.StrictMode><App gateway={gateway} /></React.StrictMode>);
