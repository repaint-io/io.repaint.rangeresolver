// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import rangeResolverGrammar from './src/grammars/rangeresolver.tmLanguage.json' with { type: 'json' };

// The GitHub Pages workflow supplies SITE_ORIGIN and SITE_BASE from actions/configure-pages, so the
// site builds correctly whether it ends up on a project page (/<repo>/) or a custom domain (/).
const site = process.env.SITE_ORIGIN || 'http://localhost:4321';
const base = process.env.SITE_BASE || '/';

export default defineConfig({
  site,
  base,
  // The grammar reference page imports RangeResolver.g4 from the Maven source tree, outside the site root.
  vite: { server: { fs: { allow: ['..'] } } },
  integrations: [
    starlight({
      title: 'Range Resolver',
      description:
        'A small dependency resolution language for Maven builds: declare version ranges, resolve them against your repositories, and lock the results.',
      logo: { src: './src/assets/logo.svg', replacesTitle: false },
      customCss: ['./src/styles/custom.css'],
      expressiveCode: {
        shiki: { langs: [rangeResolverGrammar] },
      },
      sidebar: [
        {
          label: 'Guide',
          items: [
            { label: 'Introduction', slug: 'guide/introduction' },
            { label: 'Using with Apache Maven', slug: 'guide/maven' },
            { label: 'Getting started', slug: 'guide/getting-started' },
            { label: 'How resolution works', slug: 'guide/resolution' },
            { label: 'Using the Java API', slug: 'guide/java-api' },
          ],
        },
        {
          label: 'Language',
          items: [
            { label: 'Overview', slug: 'language/overview' },
            { label: 'Repositories', slug: 'language/repositories' },
            { label: 'Resolution requests', slug: 'language/resolution' },
            { label: 'Version ranges', slug: 'language/ranges' },
            { label: 'Imports & policies', slug: 'language/imports' },
            { label: 'Aliases', slug: 'language/aliases' },
            { label: 'Docker images', slug: 'language/docker' },
            { label: 'Resolved records', slug: 'language/records' },
          ],
        },
        {
          label: 'Reference',
          items: [
            { label: 'Statement reference', slug: 'reference/statements' },
            { label: 'Grammar', slug: 'reference/grammar' },
          ],
        },
      ],
    }),
  ],
});
