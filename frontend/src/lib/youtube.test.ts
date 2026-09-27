import {describe,it,expect} from 'vitest';import {youtubeId} from './youtube';
describe('YouTube stream configuration',()=>{
  it('accepts IDs and supported URLs',()=>{expect(youtubeId('abcdefghijk')).toBe('abcdefghijk');expect(youtubeId('https://www.youtube.com/watch?v=abcdefghijk')).toBe('abcdefghijk');expect(youtubeId('https://youtu.be/abcdefghijk')).toBe('abcdefghijk');expect(youtubeId('https://www.youtube.com/live/abcdefghijk')).toBe('abcdefghijk')});
  it('rejects other hosts and invalid IDs',()=>{expect(youtubeId('https://example.com/watch?v=abcdefghijk')).toBeNull();expect(youtubeId('bad')).toBeNull()});
});
