export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const targetUrl = url.searchParams.get('url');
    const path = url.pathname.replace(/\/$/, ""); 

    // Root check
    if (path === "" || path === "/") {
      return new Response("KStream Worker is Active! API is ready.", {
        headers: { "content-type": "text/plain", "Access-Control-Allow-Origin": "*" }
      });
    }

    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, OPTIONS',
          'Access-Control-Allow-Headers': '*',
        }
      });
    }

    let response;
    try {
      if (path === '/ping') {
        response = await handlePing(targetUrl);
      } else if (path === '/fetch') {
        response = await handleFetch(targetUrl);
      } else if (path === '/paginated') {
        response = await handlePaginated(targetUrl);
      } else if (path === '/movie') {
        response = await handleMovie(targetUrl);
      } else if (path === '/poster') {
        response = await handlePoster(targetUrl);
      } else {
        response = new Response(JSON.stringify({ error: `Route ${path} not found`, ok: false }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    } catch (e) {
      response = new Response(JSON.stringify({ error: e.message, ok: false }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const newHeaders = new Headers(response.headers);
    newHeaders.set('Access-Control-Allow-Origin', '*');
    newHeaders.set('Content-Type', 'application/json');
    return new Response(response.body, {
      status: response.status,
      headers: newHeaders
    });
  }
};

async function handlePing(url) {
  if (!url) return new Response(JSON.stringify({ error: "Missing url param" }), { status: 400 });
  try {
    const res = await fetch(url, { method: 'HEAD', redirect: 'follow' });
    return new Response(JSON.stringify({ ok: res.ok, status: res.status }));
  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: e.message }));
  }
}

async function handleFetch(url) {
  try {
    const response = await fetch(url);
    const html = await response.text();
    const folders = parseFolders(html, url);
    return new Response(JSON.stringify({ folders }));
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message, ok: false }));
  }
}

async function handlePaginated(url) {
  try {
    const response = await fetch(url);
    const html = await response.text();
    const folders = parseFolders(html, url);
    const hasNextPage = html.includes('Next') || html.includes('?page=');
    return new Response(JSON.stringify({ folders, totalPages: 1, hasNextPage }));
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message, ok: false }));
  }
}

async function handleMovie(url) {
  try {
    const response = await fetch(url);
    const html = await response.text();
    const title = extractTitle(html) || "Unknown Title";
    const poster = extractPoster(html, url);
    const folders = parseFolders(html, url);
    const qualityFolders = folders.filter(f => normalizeQualityLabel(f.name));
    
    return new Response(JSON.stringify({
      title, poster,
      qualityFolders: qualityFolders.map(f => ({ ...f, label: normalizeQualityLabel(f.name) })),
      ok: true
    }));
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message, ok: false }));
  }
}

async function handlePoster(url) {
  try {
    const response = await fetch(url);
    const html = await response.text();
    const posterUrl = extractPoster(html, url);
    return new Response(JSON.stringify({ posterUrl }));
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message, ok: false }));
  }
}

function parseFolders(html, baseUrl) {
  const folders = [];
  const regex = /<a href="([^"]+)">([^<]+)<\/a>/g;
  let match;
  while ((match = regex.exec(html)) !== null) {
    const href = match[1];
    const name = match[2];
    if (href !== '../' && !href.startsWith('?')) {
      folders.push({ name, href: new URL(href, baseUrl).href });
    }
  }
  return folders;
}

function extractTitle(html) {
  const match = html.match(/<title>([^<]+)<\/title>/);
  return match ? match[1].split(' - ')[0].trim() : null;
}

function extractPoster(html, baseUrl) {
  const match = html.match(/<img[^>]+src="([^"]+)"/);
  return match ? new URL(match[1], baseUrl).href : null;
}

function normalizeQualityLabel(name) {
  const n = name.toLowerCase();
  if (n.includes('4k')) return '4K';
  if (n.includes('1080')) return '1080p';
  if (n.includes('720')) return '720p';
  if (n.includes('480')) return '480p';
  if (n.includes('360')) return '360p';
  return null;
}
