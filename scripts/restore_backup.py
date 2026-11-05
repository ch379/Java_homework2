"""Restore the supplied Navicat NB3 archive without inventing tables or seed data."""
import argparse
import gzip
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile


def convert(backup):
    statements = ['SET NAMES utf8mb4;', 'SET FOREIGN_KEY_CHECKS=0;']
    summary = {}
    with tarfile.open(backup, 'r:') as archive:
        for member in archive.getmembers():
            if not member.name.endswith('.meta.json.gz'):
                continue
            metadata = json.loads(gzip.decompress(archive.extractfile(member).read()))
            name = metadata['Name']
            if not re.fullmatch(r'[A-Za-z0-9_]+', name):
                raise ValueError('Unexpected table identifier')
            statements.append(metadata['DDL'] + ';')
            count = 0
            for part in metadata.get('Data', []):
                rows = gzip.decompress(archive.extractfile(part['Filename']).read()).decode('utf-8')
                rows = [row.strip() for row in rows.split('\x1e') if row.strip()]
                columns = ','.join('`' + field + '`' for field in metadata['Fields'])
                for row in rows:
                    statements.append(f'INSERT INTO `{name}` ({columns}) VALUES {row};')
                count += len(rows)
            summary[name] = count
    statements.append('SET FOREIGN_KEY_CHECKS=1;')
    return '\n'.join(statements), summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup', type=Path)
    parser.add_argument('--database', default='libraryweb2026')
    parser.add_argument('--user', default='root')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--mysql', default='mysql')
    parser.add_argument('--output', type=Path, help='Only export SQL, without connecting')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+', args.database):
        parser.error('Invalid database name')
    sql, summary = convert(args.backup)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(sql, encoding='utf-8')
    else:
        # Set MYSQL_PWD in this process environment; no password in command line or repository.
        cmd = [args.mysql, '-h', args.host, '-u', args.user, '--default-character-set=utf8mb4']
        exists = subprocess.run(cmd + ['-N', '-e', f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{args.database}'"], capture_output=True, check=True).stdout.strip()
        if exists != b'0':
            raise SystemExit('Target database already exists. Restore refused to protect existing data.')
        sql = f'CREATE DATABASE `{args.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\nUSE `{args.database}`;\n' + sql
        subprocess.run(cmd, input=sql.encode('utf-8'), check=True)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
