CREATE TABLE IF NOT EXISTS users (
                                     username VARCHAR(255) PRIMARY KEY,
                                     password VARCHAR(255),
                                     geminiApiKey VARCHAR(255) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS competitions (
                                            name VARCHAR(255) PRIMARY KEY,
                                            creatorUsername VARCHAR(255),
                                            ratingFormula VARCHAR(255) DEFAULT 'total',
                                            eventSeason INT DEFAULT 0,
                                            eventCode VARCHAR(50) DEFAULT '',
                                            officialEventName VARCHAR(255) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS memberships (
                                           username VARCHAR(255),
                                           competitionName VARCHAR(255),
                                           status VARCHAR(50),
                                           PRIMARY KEY(username, competitionName)
);

CREATE TABLE IF NOT EXISTS penalties (
                                         competitionName VARCHAR(255),
                                         matchNumber INT,
                                         redMajor INT DEFAULT 0,
                                         redMinor INT DEFAULT 0,
                                         blueMajor INT DEFAULT 0,
                                         blueMinor INT DEFAULT 0,
                                         redScore INT DEFAULT 0,
                                         blueScore INT DEFAULT 0,
                                         PRIMARY KEY(competitionName, matchNumber)
);

CREATE TABLE IF NOT EXISTS scores (
                                      id INT AUTO_INCREMENT PRIMARY KEY,
                                      competitionName VARCHAR(255),
                                      scoreType VARCHAR(50),
                                      matchNumber INT,
                                      alliance VARCHAR(10),
                                      team1 INT,
                                      team2 INT,
                                      team1AutoScore INT DEFAULT 0,
                                      team2AutoScore INT DEFAULT 0,
                                      team1AutoProj VARCHAR(20) DEFAULT 'NONE',
                                      team2AutoProj VARCHAR(20) DEFAULT 'NONE',
                                      team1AutoRow VARCHAR(20) DEFAULT 'NONE',
                                      team2AutoRow VARCHAR(20) DEFAULT 'NONE',
                                      autoArtifacts INT DEFAULT 0,
                                      teleopArtifacts INT DEFAULT 0,
                                      team1CanSequence BOOLEAN DEFAULT FALSE,
                                      team2CanSequence BOOLEAN DEFAULT FALSE,
                                      team1L2Climb BOOLEAN DEFAULT FALSE,
                                      team2L2Climb BOOLEAN DEFAULT FALSE,
                                      team1Ignored BOOLEAN DEFAULT FALSE,
                                      team2Ignored BOOLEAN DEFAULT FALSE,
                                      team1Broken BOOLEAN DEFAULT FALSE,
                                      team2Broken BOOLEAN DEFAULT FALSE,
                                      totalScore INT DEFAULT 0,
                                      clickLocations VARCHAR(10000),
                                      submitter VARCHAR(255),
                                      submissionTime VARCHAR(100),
                                      syncStatus VARCHAR(50) DEFAULT 'UNSYNCED'
);