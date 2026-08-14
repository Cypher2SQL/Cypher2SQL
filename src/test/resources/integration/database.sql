CREATE TABLE people (
    id INTEGER PRIMARY KEY,
    full_name TEXT NOT NULL,
    birth_year INTEGER,
    manager_id INTEGER
);

CREATE INDEX idx_people_manager_id ON people (manager_id);

CREATE TABLE movies (
    id INTEGER PRIMARY KEY,
    movie_title TEXT NOT NULL,
    author_id INTEGER,
    director_id INTEGER,
    FOREIGN KEY (author_id) REFERENCES people (id),
    FOREIGN KEY (director_id) REFERENCES people (id)
);

CREATE INDEX idx_movies_author_id ON movies (author_id);
CREATE INDEX idx_movies_director_id ON movies (director_id);

CREATE TABLE people_movies (
    person_id INTEGER NOT NULL,
    movie_id INTEGER NOT NULL,
    character_name TEXT NOT NULL,
    PRIMARY KEY (person_id, movie_id),
    FOREIGN KEY (person_id) REFERENCES people (id),
    FOREIGN KEY (movie_id) REFERENCES movies (id)
);

CREATE INDEX idx_people_movies_movie_id ON people_movies (movie_id);

INSERT INTO people (id, full_name, birth_year, manager_id) VALUES
    (1, 'Keanu Reeves', 1964, NULL),
    (2, 'Lana Wachowski', 1965, NULL),
    (3, 'Carrie-Anne Moss', 1967, 2);

INSERT INTO movies (id, movie_title, author_id, director_id) VALUES
    (10, 'The Matrix', 2, 2),
    (11, 'Speed', 1, 1);

INSERT INTO people_movies (person_id, movie_id, character_name) VALUES
    (1, 10, 'Neo'),
    (3, 10, 'Trinity'),
    (1, 11, 'Jack Traven');
