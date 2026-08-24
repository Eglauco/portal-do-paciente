// Gera src/environments/environment.ts com a URL da API a partir da
// variável de ambiente API_URL (usado no build do Railway).
// Se API_URL não estiver definida, mantém o padrão local (localhost:8080).
const fs = require('node:fs');
const path = require('node:path');

const apiUrl = (process.env.API_URL || 'http://localhost:8080').replace(/\/+$/, '');
const dir = path.join(__dirname, '..', 'src', 'environments');
fs.mkdirSync(dir, { recursive: true });

const conteudo = `// Gerado automaticamente por scripts/set-api-url.js — não editar à mão.
export const environment = {
  apiUrl: '${apiUrl}',
};
`;

fs.writeFileSync(path.join(dir, 'environment.ts'), conteudo);
console.log('[set-api-url] apiUrl =', apiUrl);
