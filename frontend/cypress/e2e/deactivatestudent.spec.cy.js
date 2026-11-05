// UC-STU-deactivate-student: the Deactivate button on the Students page and its warning. The API calls are stubbed,
// so these tests check what the page shows and what it sends; the backend's own tests cover who may deactivate and
// what deactivation does. Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const student = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}@abc.edu`,
  enabled,
  teamName: 'Team1'
})

const page = (content) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: { content, number: 0, size: 50, totalElements: content.length }
})

describe('Deactivate a student', () => {
  let johnActive

  function stubSearch() {
    cy.intercept('POST', '**/students/search*', (req) => {
      req.reply(
        page([student(4, 'John', 'Smith', johnActive), student(5, 'Eric', 'Hudson', false)])
      )
    }).as('search')
  }

  function johnsRow() {
    return cy.contains('tr', 'John')
  }

  beforeEach(() => {
    johnActive = true
    cy.login('b.gates@abc.edu', '123456')
    stubSearch()
    cy.visit('/students')
    cy.wait('@search')
  })

  it('offers Deactivate only for a student who is active', () => {
    johnsRow().find('[data-cy=deactivate-student]').should('exist')
    johnsRow().should('contain', 'Active')
    cy.contains('tr', 'Eric').should('contain', 'Deactivated')
    cy.contains('tr', 'Eric').find('[data-cy=deactivate-student]').should('not.exist')
  })

  it('warns of the consequences before anything is sent (step 4)', () => {
    cy.intercept('POST', '**/students/*/deactivate', { body: {} }).as('deactivate')

    johnsRow().find('[data-cy=deactivate-student]').click()

    cy.get('.el-message-box')
      .should('contain', 'John Smith')
      .and('contain', 'no longer be able to log in')
      .and('contain', 'weekly activity reports and peer evaluations')
      .and('contain', 'previously submitted work are kept')
      .and('contain', 'not a deletion')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('sends nothing and changes nothing when the warning is cancelled (4a)', () => {
    cy.intercept('POST', '**/students/*/deactivate', { body: {} }).as('deactivate')

    johnsRow().find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box__btns').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    johnsRow().should('contain', 'Active')
    johnsRow().find('[data-cy=deactivate-student]').should('exist')
  })

  it('deactivates on confirmation and shows the row as Deactivated (steps 5-6)', () => {
    cy.intercept('POST', '**/students/4/deactivate', (req) => {
      johnActive = false
      req.reply({
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: student(4, 'John', 'Smith', false)
      })
    }).as('deactivate')

    johnsRow().find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box__btns').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.contains('Student deactivated').should('be.visible')
    johnsRow().should('contain', 'Deactivated')
    johnsRow().find('[data-cy=deactivate-student]').should('not.exist')
  })

  it('tells the user the student is already deactivated and refreshes the row (3a)', () => {
    cy.intercept('POST', '**/students/4/deactivate', (req) => {
      johnActive = false // Someone else deactivated her first
      req.reply({
        statusCode: 409,
        body: { flag: false, code: 409, message: 'This student is already deactivated.' }
      })
    }).as('deactivate')

    johnsRow().find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box__btns').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.contains('This student is already deactivated.').should('be.visible')
    johnsRow().should('contain', 'Deactivated')
  })
})
