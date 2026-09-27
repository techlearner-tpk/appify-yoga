import {test,expect} from '@playwright/test';
test('member can sign in, see classes, and open a live class',async({page})=>{
  await page.goto('/login');
  await page.getByLabel('Email').fill('member@example.test');
  await page.getByLabel('Password').fill('DemoPass123!');
  await page.getByRole('button',{name:'Sign in →'}).click();
  await expect(page.getByRole('heading',{name:/Good day/})).toBeVisible();
  await expect(page.getByText('Yoga Everyday').first()).toBeVisible();
  await page.getByRole('link',{name:'View next class →'}).click();
  await expect(page.getByRole('heading',{name:'Yoga Everyday',level:1})).toBeVisible();
});

test('admin publishes a library image through the web app',async({page})=>{
  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('DemoPass123!');
  await page.getByRole('button',{name:'Sign in →'}).click();
  await expect(page.getByRole('heading',{name:/Good day/})).toBeVisible();
  await page.goto('/admin');
  const title=`Browser article ${Date.now()}`;
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Body').fill('A small practice for today.');
  await page.getByLabel('Image (optional)').setInputFiles({name:'one.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==','base64')});
  await page.getByRole('button',{name:'Publish article'}).click();
  await expect(page.getByText(/Published:/)).toBeVisible();
  await page.goto('/library');
  await expect(page.getByRole('heading',{name:title})).toBeVisible();
});
