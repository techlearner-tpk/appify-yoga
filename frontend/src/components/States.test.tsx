import {render,screen} from '@testing-library/react';
import {describe,it,expect} from 'vitest';
import {ErrorNotice,Loading,Empty} from './States';
describe('UI states',()=>{
  it('makes an API error accessible',()=>{render(<ErrorNotice message="Service unavailable"/>);expect(screen.getByRole('alert').textContent).toContain('Service unavailable')});
  it('shows loading and empty messages',()=>{render(<><Loading/><Empty message="No classes"/></>);expect(screen.getByText('Loading your space…')).toBeTruthy();expect(screen.getByText('No classes')).toBeTruthy()});
});
