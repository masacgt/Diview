import React, { useEffect, useState } from 'react';

interface AnimatedNumberProps {
  value: number;
  prefix?: string;
  suffix?: string;
  className?: string;
  formatter?: (val: number) => string;
  isMasked?: boolean;
  maskText?: string;
  duration?: number;
}

export const AnimatedNumber: React.FC<AnimatedNumberProps> = ({
  value,
  prefix = '',
  suffix = '',
  className = '',
  formatter = (v) => v.toLocaleString('ja-JP'),
  isMasked = false,
  maskText = '••••••••',
  duration = 600,
}) => {
  const [displayValue, setDisplayValue] = useState(value);

  useEffect(() => {
    if (isMasked) return;

    let startTimestamp: number | null = null;
    const startValue = displayValue;
    const difference = value - startValue;

    if (difference === 0) return;

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp;
      const progress = Math.min((timestamp - startTimestamp) / duration, 1);
      // easeOutCubic
      const ease = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(startValue + difference * ease);
      setDisplayValue(current);

      if (progress < 1) {
        window.requestAnimationFrame(step);
      } else {
        setDisplayValue(value);
      }
    };

    const animId = window.requestAnimationFrame(step);
    return () => window.cancelAnimationFrame(animId);
  }, [value, isMasked, duration]);

  if (isMasked) {
    return <span className={className}>{prefix}{maskText}{suffix}</span>;
  }

  return (
    <span className={className}>
      {prefix}
      {formatter(displayValue)}
      {suffix}
    </span>
  );
};
