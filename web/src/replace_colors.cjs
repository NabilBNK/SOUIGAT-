const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        file = path.resolve(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            results = results.concat(walk(file));
        } else if (file.endsWith('.tsx')) {
            results.push(file);
        }
    });
    return results;
}

const pagesDir = path.resolve(__dirname, 'pages');
const files = walk(pagesDir);

const replacements = [
    { regex: /bg-white dark:bg-\[#1a2634\]/g, replacement: 'bg-surface-800/80 backdrop-blur-md' },
    { regex: /border-slate-200 dark:border-slate-800/g, replacement: 'border-surface-700' },
    { regex: /border-slate-200 dark:border-slate-700\/30/g, replacement: 'border-surface-700/50' },
    { regex: /border border-slate-200/g, replacement: 'border border-surface-700' },
    { regex: /text-slate-900 dark:text-slate-100/g, replacement: 'text-text-primary' },
    { regex: /text-slate-600 dark:text-slate-400/g, replacement: 'text-text-secondary' },
    { regex: /text-slate-400 dark:text-slate-500/g, replacement: 'text-text-muted' },
    { regex: /text-slate-500 dark:text-slate-400/g, replacement: 'text-text-muted' },
    { regex: /text-slate-500/g, replacement: 'text-text-muted' },
    { regex: /bg-slate-100 dark:bg-\[#1e293b\]\/50/g, replacement: 'bg-surface-700/50' },
    { regex: /bg-slate-100 dark:bg-\[#1e293b\]/g, replacement: 'bg-surface-900' },
    { regex: /hover:bg-slate-100 dark:bg-\[#1e293b\]\/50/g, replacement: 'hover:bg-surface-700/50' },
    { regex: /hover:bg-slate-100 dark:bg-\[#1e293b\]/g, replacement: 'hover:bg-surface-700' },
    { regex: /text-\[#137fec\] dark:text-\[#60a5fa\]/g, replacement: 'text-brand-400' },
    { regex: /bg-\[#137fec\]\/10 dark:bg-\[#137fec\]\/20/g, replacement: 'bg-brand-500/15' },
    { regex: /text-\[#137fec\]/g, replacement: 'text-brand-500' },
    { regex: /border-slate-200/g, replacement: 'border-surface-700' },
    { regex: /dark:border-slate-800/g, replacement: 'border-surface-700' },
    { regex: /bg-white/g, replacement: 'bg-surface-800' },
    { regex: /dark:bg-\[#1a2634\]/g, replacement: 'bg-surface-800' },
    { regex: /text-slate-900/g, replacement: 'text-text-primary' },
    { regex: /dark:text-slate-100/g, replacement: 'text-text-primary' },
    { regex: /text-slate-600/g, replacement: 'text-text-secondary' },
    { regex: /dark:text-slate-400/g, replacement: 'text-text-secondary' },
    { regex: /bg-slate-50/g, replacement: 'bg-surface-900' },
    { regex: /dark:bg-[#1e293b]/g, replacement: 'bg-surface-900' },
    { regex: /border-slate-300/g, replacement: 'border-surface-600' },
    { regex: /bg-red-50/g, replacement: 'bg-red-500/10' },
    { regex: /dark:bg-red-900\/20/g, replacement: 'bg-red-500/10' },
    { regex: /text-emerald-600 dark:text-emerald-400/g, replacement: 'text-emerald-400' },
    { regex: /bg-emerald-50 dark:bg-emerald-900\/20/g, replacement: 'bg-emerald-500/10' },
    { regex: /text-emerald-600/g, replacement: 'text-emerald-500' },
    { regex: /focus:ring-brand-500\/50/g, replacement: 'focus:ring-brand-500' },
    { regex: /bg-\[#101922\]/g, replacement: 'bg-surface-900' }
];

let totalChanges = 0;

for (const file of files) {
    let content = fs.readFileSync(file, 'utf8');
    let original = content;
    
    for (const { regex, replacement } of replacements) {
        content = content.replace(regex, replacement);
    }
    
    // Clean up duplicate classes that might have been introduced
    content = content.replace(/bg-surface-\d+(?:\/\d+)?\s+bg-surface-\d+(?:\/\d+)?/g, (match) => {
        return match.split(/\s+/).pop();
    });
    content = content.replace(/text-text-\w+\s+text-text-\w+/g, (match) => {
        return match.split(/\s+/).pop();
    });
    content = content.replace(/border-surface-\d+\s+border-surface-\d+/g, (match) => {
        return match.split(/\s+/).pop();
    });
    
    if (content !== original) {
        fs.writeFileSync(file, content);
        totalChanges++;
        console.log(`Updated: ${path.relative(__dirname, file)}`);
    }
}

console.log(`Total files updated: ${totalChanges}`);
