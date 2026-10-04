const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const publicDir = path.join(__dirname, '../public');
const svgPath = path.join(publicDir, 'icon.svg');

async function generateIcons() {
  console.log('Generating PWA icons from SVG...');
  
  if (!fs.existsSync(svgPath)) {
    console.error('icon.svg not found!');
    return;
  }

  // 1. Generate apple-touch-icon (180x180)
  await sharp(svgPath)
    .resize(180, 180)
    .png()
    .toFile(path.join(publicDir, 'apple-touch-icon.png'));
  console.log('Created apple-touch-icon.png (180x180)');

  // 2. Generate standard 192x192 icon
  await sharp(svgPath)
    .resize(192, 192)
    .png()
    .toFile(path.join(publicDir, 'pwa-192x192.png'));
  console.log('Created pwa-192x192.png (192x192)');

  // 3. Generate standard 512x512 icon
  await sharp(svgPath)
    .resize(512, 512)
    .png()
    .toFile(path.join(publicDir, 'pwa-512x512.png'));
  console.log('Created pwa-512x512.png (512x512)');

  // 4. Generate maskable 512x512 icon (Center the logo with 15% safe-zone padding)
  const innerLogo = await sharp(svgPath)
    .resize(380, 380)
    .toBuffer();

  await sharp({
    create: {
      width: 512,
      height: 512,
      channels: 4,
      background: '#0b1326'
    }
  })
    .composite([{ input: innerLogo, gravity: 'center' }])
    .png()
    .toFile(path.join(publicDir, 'pwa-maskable-512x512.png'));
  
  console.log('Created pwa-maskable-512x512.png (512x512 with safe-zone margin)');
  console.log('PWA icons generated successfully!');
}

generateIcons().catch(err => {
  console.error('Error generating icons:', err);
});
