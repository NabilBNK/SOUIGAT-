import { FullConfig } from '@playwright/test';
import { exec } from 'child_process';
import util from 'util';

const execAsync = util.promisify(exec);

async function globalSetup(config: FullConfig) {
    console.log('Running global setup: Seeding test database...');
    try {
        // Run the Django management command to seed test data locally
        const { stdout, stderr } = await execAsync(
            'python manage.py seed_data',
            { cwd: '../backend' }
        );
        console.log(stdout);
        if (stderr) {
            console.error('Seed stderr:', stderr);
        }
        console.log('Database seeding complete.');
    } catch (error) {
        console.error('Failed to seed database:', error);
        throw error;
    }
}

export default globalSetup;
