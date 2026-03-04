from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0006_safe_role_migration'),
    ]

    operations = [
        # P0.2: Widen sync_log.key from VARCHAR(64) to VARCHAR(100)
        # Key format: "sync-{userId}-{64 hex chars}"
        # With BigAutoField user IDs (up to 19 digits): max = 5+19+1+64 = 89 chars
        # VARCHAR(100) provides safe margin for any Django ID type
        migrations.AlterField(
            model_name='synclog',
            name='key',
            field=models.CharField(max_length=100, unique=True),
        ),
    ]
