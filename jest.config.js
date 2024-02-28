/** @type {import('jest').Config} */
const config = {
  collectCoverageFrom: ['packages/*/src/**/*.ts'],
  coveragePathIgnorePatterns: ['.fixture.', '/fixtures/'],
  moduleFileExtensions: ['js', 'ts', 'json'],
  moduleDirectories: ['node_modules', '<rootDir>packages/*/tests/**/fixtures'],
  moduleNameMapper: {
    '^@sonar/(\\w+)(.*)$': '<rootDir>/packages/$1/src$2',
  },
  modulePathIgnorePatterns: ['<rootDir>/packages/jsts/src/rules/.*/package.json$'],
  resolver: '<rootDir>/jest-resolver.js',
  testResultsProcessor: 'jest-sonar-reporter',
  transform: {
    '^.+\\.ts$': ['ts-jest', { tsconfig: 'packages/tsconfig.test.json' }],
  },
  testMatch: [
    '<rootDir>/packages/*/tests/**/*.test.ts',
    '<rootDir>/packages/*/src/rules/**/*.test.ts',
    '<rootDir>/packages/ruling/tests/projects/*.ruling.test.ts',
  ],
  testTimeout: 20000,
};

module.exports = config;
