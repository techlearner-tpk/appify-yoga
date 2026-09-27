import {render,screen} from '@testing-library/react';import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';import {ClassAction} from './ClassAction';
describe('class timing',()=>{beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(new Date('2026-01-01T10:00:00Z'))});afterEach(()=>vi.useRealTimers());
  it('shows a countdown before class',()=>{render(<ClassAction session={{id:'one',starts_at:'2026-01-01T10:10:00Z',ends_at:'2026-01-01T11:00:00Z'}}/>);expect(screen.getByText('In 10 min ↗')).toBeTruthy()});
  it('shows join only while live',()=>{render(<ClassAction session={{id:'one',starts_at:'2026-01-01T09:00:00Z',ends_at:'2026-01-01T11:00:00Z'}}/>);expect(screen.getByText('Join live ↗')).toBeTruthy()});
  it('shows completed after class',()=>{render(<ClassAction session={{id:'one',starts_at:'2026-01-01T08:00:00Z',ends_at:'2026-01-01T09:00:00Z'}}/>);expect(screen.getByText('Completed ✓')).toBeTruthy()});
});
