import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserSearch } from './user-search';

describe('UserSearch', () => {
  let component: UserSearch;
  let fixture: ComponentFixture<UserSearch>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserSearch],
    }).compileComponents();

    fixture = TestBed.createComponent(UserSearch);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
