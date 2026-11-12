import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import {LazyMotion, domAnimation, MotionConfig} from 'motion/react';
import './styles.css';
createRoot(document.getElementById('root')!).render(<StrictMode><LazyMotion features={domAnimation} strict><MotionConfig reducedMotion="user"><App /></MotionConfig></LazyMotion></StrictMode>);
